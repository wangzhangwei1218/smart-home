/*
 * 文件名: main.c
 * 功  能: 智能家居主控程序
 *         负责整合温湿度采集、光照采集、LCD显示、串口通信、
 *         继电器控制以及步进电机（窗帘）驱动，实现自动与手动两种控制模式。
 * 硬件平台: STC89C52 (51单片机)
 * 晶振频率: 11.0592 MHz
 */

#include <reg52.h>
#include <intrins.h>
#include <math.h>

/* 常用数据类型别名，简化书写 */
#define uchar unsigned char
#define uint  unsigned int
#define ulong unsigned long

#include "1602.h"      /* LCD1602 显示驱动（内联头文件） */
#include "ad.h"        /* ADC0832 模数转换驱动（内联头文件） */
#include "DS18B20.h"   /* DS18B20 温度传感器驱动 */
#include "autor.h"     /* 步进电机驱动 */

/* LCD 第一行初始显示字符串：湿度占位符 */
uchar code zifu0[] = "Humi:00.0%RH "; 
/* LCD 第二行初始显示字符串：温度与光照占位符 */
uchar code zifu1[] = "Temp:00.0C Le:00"; 

/* 温度上限阈值（℃），超过该值时触发开窗/开灯动作 */
char TH = 26;
/* 温度下限阈值（℃），低于该值时触发关窗/关灯动作 */
char TL = 23;
/* DS18B20 读取的当前温度整数值（℃） */
int ans1;              
/* 通用标志位，保留备用 */
uchar flag;            

/* P3.6 控制继电器：0=断开，1=闭合 */
sbit out = P3^6;
/* P3.7 控制蜂鸣器：0=响，1=静 */
sbit buz = P3^7;
/* ADC0832 通道0 采集的湿度原始值（已按比例换算，单位：0.1%RH） */
ulong u2 = 0;
/* ADC0832 通道1 采集的光照原始值（已按比例换算，单位：Lv档位） */
ulong i2 = 0;

/* 当前窗帘状态：0=关闭，1=打开 */
uchar curtainState = 0;
/* 光照强度阈值：达到或超过此值时自动开窗（单位与 i2 相同） */
#define L_OPEN         8
/* 光照强度阈值：低于或等于此值时自动关窗 */
#define L_CLOSE        5
/* 步进电机每次执行开/关窗所走的步数（影响窗帘行程） */
#define CURTAIN_STEPS  40

/* 串口接收缓冲区（最多接收 19 个可见字符 + 结束符） */
uchar rxBuf[20];      
/* 当前已接收的字符数 */
uchar rxIdx = 0;      
/* 接收完成标志：1=已收到一条完整指令，等待处理 */
uchar rxFlag = 0;     
/* 数据上报计数器，每循环加1，超过阈值时通过串口向上位机发送传感器数据 */
uint  sendTick = 0;   
/* 控制模式：0=自动（根据传感器数值自动控制），1=手动（由 APP 下发指令控制） */
uchar manualMode = 0;

/*
 * 函数名: UartInit
 * 功  能: 初始化串口（UART1），波特率 9600 bps，8位数据，无校验，1位停止位
 *         使用定时器1（模式2，8位自动重装）提供波特率时钟
 */
void UartInit(void) {
    SCON = 0x50;  /* 串口工作模式1：8位UART，REN=1使能接收 */
    TMOD &= 0x0F; /* 清除定时器1的模式位（高4位），保留定时器0配置 */
    TMOD |= 0x20; /* 定时器1设为模式2（8位自动重装载） */
    TH1 = 0xFD;   /* 11.0592MHz 晶振下，波特率9600对应的重装值 */
    TL1 = 0xFD;
    TR1 = 1;      /* 启动定时器1 */
    ES  = 1;      /* 使能串口中断 */
    EA  = 1;      /* 全局中断使能 */
}

/*
 * 函数名: UartSendChar
 * 功  能: 通过串口发送单个字节，发送完成或超时后返回
 * 参  数: c — 要发送的字符
 */
void UartSendChar(uchar c) {
    uint timeout = 0;
    SBUF = c;                                  /* 将字符写入发送缓冲寄存器，自动启动发送 */
    while (!TI && ++timeout < 10000);          /* 等待发送完成标志 TI 置1，或超时退出 */
    TI = 0;                                    /* 清除发送完成标志，准备下一次发送 */
}

/*
 * 函数名: UartSendString
 * 功  能: 发送以 '\0' 结尾的字符串
 * 参  数: str — 指向字符串首地址的指针
 */
void UartSendString(uchar *str) {
    while (*str) UartSendChar(*str++);         /* 逐字节发送，遇到结束符停止 */
}

/*
 * 函数名: UartSendNum
 * 功  能: 将整数以 ASCII 字符串形式通过串口发送（支持负数）
 * 参  数: num — 待发送的整数（范围适合 int）
 */
void UartSendNum(int num) {
    uchar buf[6];                              /* 临时缓冲区，最多5位数字+符号 */
    uchar i = 0;
    if (num == 0) { UartSendChar('0'); return; }
    if (num < 0)  { UartSendChar('-'); num = -num; }  /* 负数先发'-' */
    while (num > 0) {
        buf[i++] = (num % 10) + '0';          /* 低位先存 */
        num /= 10;
    }
    while (i > 0) {
        UartSendChar(buf[--i]);                /* 高位先发，实现正序输出 */
    }
}

/*
 * 函数名: Uart_Isr
 * 功  能: 串口接收中断服务函数（中断号4对应UART中断）
 *         每收到一个字节追加到缓冲区；
 *         收到换行符（\n 或 \r）时置位 rxFlag，表示一条指令接收完毕。
 */
void Uart_Isr() interrupt 4 {
    if (RI) {
        uchar c = SBUF;                        /* 读取接收到的字节 */
        RI = 0;                                /* 清除接收完成标志 */
        if (rxFlag == 1) return;               /* 上一条指令尚未处理，丢弃新数据 */
        if (c == '\n' || c == '\r') {
            if (rxIdx > 0) {
                rxBuf[rxIdx] = '\0';           /* 在缓冲区末尾补充字符串结束符 */
                rxFlag = 1;                    /* 标记一条完整指令已就绪 */
            }
        } else {
            rxBuf[rxIdx++] = c;                /* 将字符存入缓冲区 */
            if (rxIdx >= 20) rxIdx = 0;        /* 防止缓冲区溢出，溢出时重置索引 */
        }
    }
}

/*
 * 函数名: GetNumFromStr
 * 功  能: 从字符串头部解析整数（支持负号），遇到非数字字符停止
 * 参  数: str — 指向待解析字符串的指针
 * 返回值: 解析得到的整数
 */
int GetNumFromStr(uchar *str) {
    int res = 0;
    uchar sign = 1;
    if (*str == '-') { sign = -1; str++; }     /* 处理负号 */
    while (*str >= '0' && *str <= '9') {
        res = res * 10 + (*str - '0');
        str++;
    }
    return res * sign;
}

/*
 * 函数名: delay1
 * 功  能: 软件延时（粗略估算，约 x 毫秒量级）
 * 参  数: x — 外层循环次数，越大延时越长
 */
void delay1(uchar x) {
    uchar i, j, k;
    for(k=x; k>0; k--)
        for(i=20; i>0; i--)
            for(j=248; j>0; j--);
}

/*
 * 函数名: BeepOnce
 * 功  能: 驱动蜂鸣器短响一次，用于操作确认提示
 */
void BeepOnce(void) {
    buz = 0; delay1(10);                       /* 拉低蜂鸣器引脚，开始发声 */
    buz = 1; delay1(10);                       /* 拉高引脚，停止发声，形成一次短鸣 */
}

/*
 * 函数名: xianshi
 * 功  能: 读取 ADC0832 的两路模拟量，换算为湿度（u2）和光照（i2），
 *         并将温度、湿度、光照数值更新到 LCD1602 的对应显示位置
 */
void xianshi(void) {
    /* 通道0：湿度传感器，量程映射 0~255 → 0~300（即 0.0%RH ~ 30.0%RH，精度0.1%） */
    u2 = adc08322(0) * (ulong)300 / (ulong)255; 
    /* 通道1：光敏传感器，量程映射 0~255 → 0~15（档位0~15） */
    i2 = adc08322(1) * (ulong)15  / (ulong)255; 

    /* 更新 LCD 第一行湿度显示（地址 0x05 为 "Humi:" 后第一位） */
    lcd1602_adr(0x05);
    lcd1602_writenumber(0x30 + u2%1000/100);   /* 十位数字 */
    lcd1602_writenumber(0x30 + u2%100/10);     /* 个位数字 */
    lcd1602_writenumber('.');
    lcd1602_writenumber(0x30 + u2%10);         /* 小数点后一位 */

    /* 更新 LCD 第二行温度显示（地址 0x45 为 "Temp:" 后第一位） */
    lcd1602_adr(0x45);
    lcd1602_writenumber(0x30 + ans1%100/10);   /* 温度十位 */
    lcd1602_writenumber(0x30 + ans1%10);       /* 温度个位 */
    lcd1602_writenumber('.');
    lcd1602_writenumber(0x30 + 0);             /* 小数位始终显示0（DS18B20整数读取） */

    /* 更新 LCD 第二行光照显示（地址 0x4E 为 "Le:" 后第一位） */
    lcd1602_adr(0x4e);
    lcd1602_writenumber(0x30 + i2%100/10);     /* 光照十位 */
    lcd1602_writenumber(0x30 + i2%10);         /* 光照个位 */
}

/*
 * 函数名: Curtain_Open
 * 功  能: 驱动步进电机正转 CURTAIN_STEPS 步，打开窗帘，并更新状态标志为"已开"
 */
void Curtain_Open(void) {
    uchar n;
    for(n=0; n<CURTAIN_STEPS; n++) MotorRight();
    curtainState = 1;                          /* 更新窗帘状态为"已打开" */
}

/*
 * 函数名: Curtain_Close
 * 功  能: 驱动步进电机反转 CURTAIN_STEPS 步，关闭窗帘，并更新状态标志为"已关"
 */
void Curtain_Close(void) {
    uchar n;
    for(n=0; n<CURTAIN_STEPS; n++) MotorLeft(50);
    curtainState = 0;                          /* 更新窗帘状态为"已关闭" */
}

/*
 * 函数名: main
 * 功  能: 程序主入口
 *         完成初始化后进入主循环，依次执行：
 *           1. 刷新 LCD 显示
 *           2. 读取 DS18B20 温度
 *           3. 解析并处理串口指令（OUT/SETTH/SETTL）
 *           4. 自动控制模式下根据光照值控制窗帘和继电器
 *           5. 定时通过串口向上位机上报传感器数据
 */
void main(void) {
    lcd1602_init();                            /* 初始化 LCD1602 显示屏 */
    UartInit();                                /* 初始化串口，波特率9600 */

    LCD1602_string(1,1,zifu0);                 /* 在第1行第1列显示湿度初始字符串 */
    LCD1602_string(2,1,zifu1);                 /* 在第2行第1列显示温度/光照初始字符串 */

    buz = 1;                                   /* 关闭蜂鸣器（高电平静音） */
    out = 1;                                   /* 继电器初始状态为闭合（可根据硬件调整） */
    curtainState = 1;                          /* 上电时默认窗帘为打开状态 */

    while(1) {
        xianshi();                             /* 刷新 LCD 上的湿度、温度、光照显示 */
        DS18B20_start_change1();               /* 通知 DS18B20 开始温度转换 */
        ans1 = DS18B20_read_date1();           /* 读取上次转换完成的温度整数值 */

        /* ----------------------------------------------------------------
         * 步骤1：处理从串口收到的 APP 指令
         *   OUT:1  — 手动开启（继电器闭合 + 开窗）
         *   OUT:0  — 手动关闭（继电器断开 + 关窗）
         *   SETTH:<n> — 设置温度上限阈值，切回自动模式
         *   SETTL:<n> — 设置温度下限阈值，切回自动模式
         * ---------------------------------------------------------------- */
        if (rxFlag) {
            if (rxBuf[0]=='O' && rxBuf[1]=='U' && rxBuf[2]=='T' && rxBuf[3]==':') {
                manualMode = 1;                /* 收到 OUT 指令，切换为手动控制模式 */
                if (rxBuf[4] == '1') {
                    out = 1;                   /* 继电器闭合（开灯/开设备） */
                    if (curtainState == 0) Curtain_Open();   /* 若窗帘关着则打开 */
                } 
                else {
                    out = 0;                   /* 继电器断开（关灯/关设备） */
                    if (curtainState == 1) Curtain_Close();  /* 若窗帘开着则关闭 */
                }
                BeepOnce();                    /* 蜂鸣确认 */
            }
            else if (rxBuf[0]=='S' && rxBuf[1]=='E' && rxBuf[2]=='T' && rxBuf[3]=='T' && rxBuf[4]=='H' && rxBuf[5]==':') {
                TH = GetNumFromStr(&rxBuf[6]); /* 解析并更新温度上限阈值 */
                manualMode = 0;                /* 切回自动控制模式 */
                BeepOnce();
            }
            else if (rxBuf[0]=='S' && rxBuf[1]=='E' && rxBuf[2]=='T' && rxBuf[3]=='T' && rxBuf[4]=='L' && rxBuf[5]==':') {
                TL = GetNumFromStr(&rxBuf[6]); /* 解析并更新温度下限阈值 */
                manualMode = 0;                /* 切回自动控制模式 */
                BeepOnce();
            }
            rxIdx = 0;                         /* 重置接收索引，准备接收下一条指令 */
            rxFlag = 0;                        /* 清除接收完成标志 */
        }

        /* ----------------------------------------------------------------
         * 步骤2：自动控制模式
         *   当光照值 >= L_OPEN 且窗帘未开 → 自动开窗并打开继电器
         *   当光照值 <= L_CLOSE 且窗帘未关 → 自动关窗并断开继电器
         * ---------------------------------------------------------------- */
        if (manualMode == 0) { 
            if((i2 >= L_OPEN) && (curtainState == 0)) {
                Curtain_Open();                /* 光照充足，自动开窗 */
                out = 1;                       /* 打开继电器 */
                BeepOnce();
            }
            else if((i2 <= L_CLOSE) && (curtainState == 1)) {
                Curtain_Close();               /* 光照不足，自动关窗 */
                out = 0;                       /* 断开继电器 */
                BeepOnce();
            }
        }

        /* ----------------------------------------------------------------
         * 步骤3：定时上报传感器数据到串口
         *   格式: T:<温度>.0,H:<湿度整数>.<湿度小数>,L:<光照>\r\n
         *   约每 25 个主循环上报一次
         * ---------------------------------------------------------------- */
        sendTick++;
        if(sendTick > 25) {
            sendTick = 0;
            UartSendString("T:"); UartSendNum(ans1); UartSendString(".0");
            UartSendString(",H:"); UartSendNum((int)(u2/10)); UartSendChar('.'); UartSendChar((uchar)('0'+(u2%10)));
            UartSendString(",L:"); UartSendNum(i2);
            UartSendString("\r\n");
        }
    }
}

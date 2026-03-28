#include <reg52.h>
#include <intrins.h>
#include <math.h>

#define uchar unsigned char
#define uint  unsigned int
#define ulong unsigned long

#include "1602.h"      // LCD1602驱动
#include "ad.h"        // ADC0832驱动
#include "DS18B20.h"   // 温度传感器驱动
#include "autor.h"     // 电机驱动

uchar code zifu0[] = "Humi:00.0%RH "; 
uchar code zifu1[] = "Temp:00.0C Le:00"; 

char TH = 26, TL = 23; // 上下限阈值
int ans1;              
uchar flag;            

sbit out = P3^6; // 继电器
sbit buz = P3^7; // 蜂鸣器
ulong u2 = 0;    // 湿度值
ulong i2 = 0;    // 光照值

uchar curtainState = 0;         // 0:关, 1:开
#define L_OPEN         8        // 开窗阈值
#define L_CLOSE        5        // 关窗阈值
#define CURTAIN_STEPS  40       // 电机步数

uchar rxBuf[20];      
uchar rxIdx = 0;      
uchar rxFlag = 0;     
uint  sendTick = 0;   
uchar manualMode = 0; // 0:自动, 1:APP手动

// 串口初始化
void UartInit(void) {
    SCON = 0x50;  
    TMOD &= 0x0F; 
    TMOD |= 0x20; 
    TH1 = 0xFD;   
    TL1 = 0xFD;
    TR1 = 1;      
    ES  = 1;      
    EA  = 1;      
}

// 发送字符
void UartSendChar(uchar c) {
    uint timeout = 0;
    SBUF = c;
    while (!TI && ++timeout < 10000); 
    TI = 0;
}

// 发送字符串
void UartSendString(uchar *str) {
    while (*str) UartSendChar(*str++);
}

// 发送数字
void UartSendNum(int num) {
    uchar buf[6];
    uchar i = 0;
    if (num == 0) { UartSendChar('0'); return; }
    if (num < 0)  { UartSendChar('-'); num = -num; }
    while (num > 0) {
        buf[i++] = (num % 10) + '0';
        num /= 10;
    }
    while (i > 0) {
        UartSendChar(buf[--i]);
    }
}

// 串口中断
void Uart_Isr() interrupt 4 {
    if (RI) {
        uchar c = SBUF;
        RI = 0;
        if (rxFlag == 1) return; 
        if (c == '\n' || c == '\r') {
            if (rxIdx > 0) {
                rxBuf[rxIdx] = '\0';
                rxFlag = 1; 
            }
        } else {
            rxBuf[rxIdx++] = c;
            if (rxIdx >= 20) rxIdx = 0; 
        }
    }
}

// 解析数字
int GetNumFromStr(uchar *str) {
    int res = 0;
    uchar sign = 1;
    if (*str == '-') { sign = -1; str++; }
    while (*str >= '0' && *str <= '9') {
        res = res * 10 + (*str - '0');
        str++;
    }
    return res * sign;
}

void delay1(uchar x) {
    uchar i, j, k;
    for(k=x; k>0; k--)
        for(i=20; i>0; i--)
            for(j=248; j>0; j--);
}

void BeepOnce(void) {
    buz = 0; delay1(10);
    buz = 1; delay1(10);
}

// 刷新LCD显示
void xianshi(void) {
    u2 = adc08322(0) * (ulong)300 / (ulong)255; 
    i2 = adc08322(1) * (ulong)15  / (ulong)255; 

    lcd1602_adr(0x05);
    lcd1602_writenumber(0x30 + u2%1000/100);
    lcd1602_writenumber(0x30 + u2%100/10);
    lcd1602_writenumber('.');
    lcd1602_writenumber(0x30 + u2%10);

    lcd1602_adr(0x45);
    lcd1602_writenumber(0x30 + ans1%100/10);
    lcd1602_writenumber(0x30 + ans1%10);
    lcd1602_writenumber('.');
    lcd1602_writenumber(0x30 + 0);

    lcd1602_adr(0x4e);
    lcd1602_writenumber(0x30 + i2%100/10);
    lcd1602_writenumber(0x30 + i2%10);
}

void Curtain_Open(void) {
    uchar n;
    for(n=0; n<CURTAIN_STEPS; n++) MotorRight();
    curtainState = 1;
}

void Curtain_Close(void) {
    uchar n;
    for(n=0; n<CURTAIN_STEPS; n++) MotorLeft(50);
    curtainState = 0;
}

void main(void) {
    lcd1602_init();
    UartInit(); 

    LCD1602_string(1,1,zifu0);
    LCD1602_string(2,1,zifu1);

    buz = 1;  
    out = 1;  
    curtainState = 1; 

    while(1) {
        xianshi(); 
        DS18B20_start_change1();
        ans1 = DS18B20_read_date1();

        // 1. 串口指令解析
        if (rxFlag) {
            if (rxBuf[0]=='O' && rxBuf[1]=='U' && rxBuf[2]=='T' && rxBuf[3]==':') {
                manualMode = 1; 
                if (rxBuf[4] == '1') {
                    out = 1; 
                    if (curtainState == 0) Curtain_Open(); 
                } 
                else {
                    out = 0; 
                    if (curtainState == 1) Curtain_Close(); 
                }
                BeepOnce(); 
            }
            else if (rxBuf[0]=='S' && rxBuf[1]=='E' && rxBuf[2]=='T' && rxBuf[3]=='T' && rxBuf[4]=='H' && rxBuf[5]==':') {
                TH = GetNumFromStr(&rxBuf[6]);
                manualMode = 0; 
                BeepOnce();
            }
            else if (rxBuf[0]=='S' && rxBuf[1]=='E' && rxBuf[2]=='T' && rxBuf[3]=='T' && rxBuf[4]=='L' && rxBuf[5]==':') {
                TL = GetNumFromStr(&rxBuf[6]);
                manualMode = 0; 
                BeepOnce();
            }
            rxIdx = 0;
            rxFlag = 0; 
        }

        // 2. 自动控制模式
        if (manualMode == 0) { 
            if((i2 >= L_OPEN) && (curtainState == 0)) {
                Curtain_Open(); 
                out = 1; 
                BeepOnce();
            }
            else if((i2 <= L_CLOSE) && (curtainState == 1)) {
                Curtain_Close(); 
                out = 0; 
                BeepOnce();
            }
        }

        // 3. 上报数据
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
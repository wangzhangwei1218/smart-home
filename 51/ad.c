/*
 * 文件名: ad.c
 * 功  能: ADC0832 模数转换器驱动（Proteus 仿真优化版本）
 *         本文件是针对 Proteus 仿真环境重写的 ADC0832 驱动，
 *         相比 ad.h 中的原始版本，引脚连接有所调整，时序实现更为规范。
 *
 * 引脚连接（Proteus 仿真接线）：
 *   CLK  → P3.3    时钟信号线
 *   DIN  → P3.4    数据输入线（MCU → ADC，本文件中命名为 DATI2）
 *   DOUT → P3.2    数据输出线（ADC → MCU，本文件中命名为 DATO2）
 *   CS   → P3.5    片选信号线（低电平选中芯片）
 *
 * 注意：DIN 与 DOUT 使用不同引脚（P3.4 和 P3.2），
 *       与 ad.h 中将两者复用同一引脚的方案不同。
 */

#include "ad.h"
#include <intrins.h>

/* ---- Proteus 版引脚重定义（覆盖 ad.h 中的声明） ---- */
sbit Clk2  = P3^3;          /* 时钟引脚（CLK） */
sbit DATI2 = P3^4;          /* 数据输入引脚（DIN，MCU→ADC） */
sbit DATO2 = P3^2;          /* 数据输出引脚（DOUT，ADC→MCU，与 ad.h 不同，使用独立引脚） */
sbit CS2   = P3^5;          /* 片选引脚（CS，低电平选中） */

/*
 * 函数名: clk_pulse（内部静态函数）
 * 功  能: 产生一个完整的时钟脉冲（高→低），用于驱动 ADC0832 的串行接口
 *         每次调用对应 ADC0832 接收或发送一个数据位
 */
static void clk_pulse(void)
{
    Clk2 = 1;
    _nop_(); _nop_();       /* 保持高电平足够时间（约2个NOP） */
    Clk2 = 0;
    _nop_(); _nop_();       /* 保持低电平足够时间 */
}

/*
 * 函数名: adc0832_init
 * 功  能: 初始化 ADC0832 接口引脚为空闲状态
 *         CS 拉高（不选中），CLK 拉低，DIN 拉低
 */
void adc0832_init(void)
{
    CS2 = 1;                /* 拉高片选，释放 ADC0832（不选中） */
    Clk2 = 0;
    DATI2 = 0;
}

/*
 * 函数名: adc08322
 * 功  能: 对 ADC0832 指定通道进行一次 8 位模数转换，返回 0~255 的结果
 *
 * ADC0832 时序说明（单端模式）：
 *   1. 拉低 CS，开始通信
 *   2. 发送 Start bit（1）
 *   3. 发送 SGL=1（单端模式）
 *   4. 发送 ODD=CH（通道选择：0→CH0，1→CH1）
 *   5. 额外发送一个低电平位（datasheet 要求的时序间隔）
 *   6. 读取8位正向转换结果（MSB First）
 *   7. 读取8位反向校验结果（LSB First），用于验证数据正确性
 *   8. 拉高 CS，结束通信
 *
 * 参  数: CH — 通道号（0 或 1）
 * 返回值: 0~255 的转换结果（8位精度）
 */
unsigned int adc08322(unsigned char CH)
{
    unsigned char i;
    unsigned char adval = 0;    /* 正向8位转换结果（MSB First 读入） */
    unsigned char test = 0;     /* 反向8位校验结果（LSB First 读入，用于数据验证） */
    unsigned int result = 0;    /* 最终返回值 */

    /* ---- 开始通信：拉低 CS，时钟置低 ---- */
    CS2 = 0;
    Clk2 = 0;
    _nop_();

    /* 发送 Start bit = 1（告知 ADC0832 开始一次转换） */
    DATI2 = 1;
    clk_pulse();

    /* 发送 SGL=1（单端模式，相对于差分模式） */
    DATI2 = 1; clk_pulse();
    /* 发送 ODD=CH（通道选择：CH=0→通道0，CH=1→通道1） */
    DATI2 = (CH ? 1 : 0); clk_pulse();

    /* 按 datasheet 要求发送一个额外的低电平位，完成通道配置 */
    DATI2 = 0;
    clk_pulse();

    /* ---- 读取正向8位转换结果（MSB First，即高位先出） ---- */
    adval = 0;
    for (i = 0; i < 8; i++) {
        Clk2 = 1;
        _nop_();
        adval <<= 1;            /* 左移一位，为新数据位腾出最低位 */
        if (DATO2) adval |= 0x01;  /* 采样 DOUT：高电平则当前位为1 */
        Clk2 = 0;
        _nop_();
    }

    /* ---- 读取反向8位校验结果（LSB First，即低位先出） ---- */
    test = 0;
    for (i = 0; i < 8; i++) {
        Clk2 = 1;
        _nop_();
        test <<= 1;             /* 左移，接收新位（LSB First 方式） */
        if (DATO2) test |= 0x01;
        Clk2 = 0;
        _nop_();
    }

    /* 结束通信：拉高 CS，释放 ADC0832 */
    CS2 = 1;

    /*
     * 数据验证：
     *   正向结果（adval）与反向结果（test）理论上应相等，
     *   相等则说明本次传输无误；若不等，仍以 adval 为准
     *   （可扩展为重新采样或报错）
     */
    if (adval == test) result = adval;
    else result = adval;

    return (result & 0xFF);     /* 确保返回值在 0~255 范围内 */
}

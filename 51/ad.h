/*
 * 文件名: ad.h
 * 功  能: ADC0832 模数转换器驱动（内联实现头文件）
 *         ADC0832 是一款 8位、2通道串行模数转换器，通过三线 SPI 接口与单片机通信。
 *         本文件以内联方式提供转换函数，直接被 main.c 通过 #include 引用。
 *
 * 引脚连接（与 ad.c 的 Proteus 版本有所不同，请以实际硬件为准）：
 *   CLK  → P3.3    时钟信号线
 *   DIN  → P3.4    数据输入线（MCU → ADC）
 *   DOUT → P3.4    数据输出线（ADC → MCU，此处与 DIN 共用引脚，适用于半双工接法）
 *   CS   → P3.5    片选信号线（低电平选中芯片）
 *
 * 注意：DATI2 与 DATO2 均映射到 P3.4，这是因为在实际时序中
 *       DIN 和 DOUT 不会同时驱动，可通过分时复用同一引脚实现。
 */

#ifndef __AD_H__             /* 防止头文件被重复包含的条件编译开始 */
#define __AD_H__
#include <reg52.h>
#include <intrins.h>

/* ADC 采样结果暂存变量（全局），保存最近一次转换值 */
unsigned long dat = 0x00; 

/* ADC0832 接口引脚定义 */
sbit  Clk2= P3^3;           /* 时钟引脚（CLK） */
sbit  DATI2=P3^4;           /* 数据输入引脚（DIN，MCU→ADC） */
sbit  DATO2= P3^4;          /* 数据输出引脚（DOUT，ADC→MCU，与DIN复用同一引脚） */
sbit  CS2= P3^5;            /* 片选引脚（CS，低电平有效） */
/* 辅助变量，保留备用 */
unsigned int  dat2 = 0x00;

/*
 * 函数名: adc08322
 * 功  能: 对 ADC0832 指定通道进行一次 8 位模数转换，返回转换结果
 *         通道选择：CH=0 → 通道0（CH0），CH=1 → 通道1（CH1）
 *         本实现采用单端模式（SGL=1），通道编号由 ODD 位控制。
 * 参  数: CH — 通道号（0 或 1）
 * 返回值: 0~255 的无符号整数，代表该通道当前的模拟量数字化结果
 */
unsigned int adc08322(unsigned char CH)
{
  unsigned char i,test,adval;
  adval = 0x00;
  test = 0x00;

  /* ---- 初始化：拉低时钟，抬高 DIN，片选使能 ---- */
  Clk2 = 0;      
  DATI2 = 1;
  _nop_();   _nop_();
  CS2 = 0;                  /* CS 拉低，选中 ADC0832 */
  _nop_();
  Clk2 = 1;
  _nop_();  _nop_();

  /* ---- 通道选择位发送：按 ADC0832 时序写入 SGL 和 ODD 两个控制位 ---- */
  if(CH == 0x00)            /* 选择通道0 */
   {
       Clk2 = 0;
       DATI2 = 1;           /* 通道0 控制位第一位（SGL=1，单端模式） */
       _nop_();
       Clk2 = 1;
       _nop_();  _nop_();

       Clk2 = 0;
       DATI2 = 0;           /* 通道0 控制位第二位（ODD=0，选CH0） */
       _nop_();  _nop_();

       Clk2 = 1;
       _nop_();
    } 
    else                    /* 选择通道1 */
    {
       Clk2 = 0;
       DATI2 = 1;           /* 通道1 控制位第一位（SGL=1，单端模式） */
        _nop_();  _nop_();

       Clk2 = 1;
        _nop_();  _nop_();

       Clk2 = 0;
       DATI2 = 1;           /* 通道1 控制位第二位（ODD=1，选CH1） */
      _nop_();
      Clk2 = 1;
      _nop_();
    }
      Clk2 = 0;   _nop_();

      DATI2 = 1;
  /* ---- 读取正向8位转换结果（MSB First） ---- */
   for( i = 0;i < 8;i++ )
    {
       _nop_();
       adval <<= 1;          /* 左移，准备接收新位 */
       Clk2 = 1;
       _nop_();  _nop_();

       Clk2 = 0;   _nop_();

       if (DATO2)
          adval |= 0x01;     /* DOUT 为高则当前位为1 */
      else
          adval |= 0x00;
    }
  /* ---- 读取反向8位校验结果（LSB First，用于与正向结果核对） ---- */
  for (i = 0; i < 8; i++)
      {
           test >>= 1;       /* 右移，准备接收新位（LSB First） */
           if (DATO2)
              test |= 0x80;  /* DOUT 为高则当前最高位为1 */
           else 
              test |= 0x00;
          _nop_();
          Clk2 = 1;
          _nop_();  _nop_();

          Clk2 = 0;   _nop_();

      }
  /*
   * 比较正向结果（adval）与反向结果（test）：
   *   相同 → 数据有效，赋值给 dat
   *   不同 → 本次转换可能受干扰，仍使用 adval（可改为丢弃或重试）
   */
  if (adval == test)     
     dat = test;
     _nop_();  _nop_();
     CS2 = 1;                /* 释放 ADC0832，结束本次转换 */
     DATO2 = 1;
     Clk2 = 1;
     return dat;
}

#endif                       /* 防止头文件被重复包含的条件编译结束 */

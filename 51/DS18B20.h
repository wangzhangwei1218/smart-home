/*
 * 文件名: DS18B20.h
 * 功  能: DS18B20 温度传感器驱动头文件
 *         定义公共数据类型别名、1-Wire 时序相关宏、数据引脚以及对外函数声明。
 */

#ifndef _DS18B20_H_          /* 防止头文件被重复包含的条件编译开始 */
#define _DS18B20_H_

#include <reg52.h>           /* 51单片机寄存器定义（SFR、sbit等） */
#include <intrins.h>         /* 包含 _nop_() 空指令函数 */

/* 常用数据类型别名，简化代码书写 */
#define uchar  unsigned char
#define uint   unsigned int
#define ulong   unsigned long

/*
 * NOPS 宏：连续执行 4 个 NOP（空指令），约产生 4 µs 的延时
 * 用于在 1-Wire 时序中产生精确的微秒级等待
 */
#define NOPS(); { _nop_();_nop_();_nop_();_nop_();}

/* DS18B20 数据引脚定义：连接到 P2.0 */
sbit DQ1=P2^0;

/* ---- 对外函数声明 ---- */

/* 启动 DS18B20 温度转换（需等待转换完成后再调用读取函数） */
void DS18B20_start_change1();

/* 读取 DS18B20 上一次转换的温度整数值（℃） */
int DS18B20_read_date1();

#endif                       /* 防止头文件被重复包含的条件编译结束 */

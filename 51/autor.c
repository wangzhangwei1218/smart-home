/*
 * 文件名: autor.c
 * 功  能: 步进电机驱动实现
 *         驱动一个4相8拍步进电机（常见型号如 28BYJ-48），
 *         通过 GPIO_MOTOR（P0口）输出对应的相序信号，
 *         实现电机正转（右旋/开窗）和反转（左旋/关窗）。
 *
 * 8拍驱动相序说明：
 *   正转序列 FFW：{0xF1,0xF3,0xF2,0xF6,0xF4,0xFC,0xF8,0xF9}
 *   反转序列 FFZ：{0xF9,0xF8,0xFC,0xF4,0xF6,0xF2,0xF3,0xF1}
 *   （与 FFW 逆序，实现反转）
 *
 * 输出时使用 & 0x1F 屏蔽高3位，保留低5位有效驱动信号。
 */

#include <reg52.h>
#include "autor.h"

/* 常用数据类型别名 */
#define uchar unsigned char
#define uint  unsigned int

/* 步进电机正转（右旋）相序表：8拍驱动，依次输出即可使电机顺时针转动 */
uchar code FFW[8] = {0xf1,0xf3,0xf2,0xf6,0xf4,0xfc,0xf8,0xf9};
/* 步进电机反转（左旋）相序表：为 FFW 的逆序，使电机逆时针转动 */
uchar code FFZ[8] = {0xf9,0xf8,0xfc,0xf4,0xf6,0xf2,0xf3,0xf1};

/*
 * 函数名: StepDelay
 * 功  能: 步进电机专用延时函数
 *         每个相位保持的时间长短决定电机转速：时间越长转速越慢，
 *         时间太短则电机可能因来不及响应而失步。
 * 参  数: t — 延时系数，每个单位约对应 80 次空循环
 */
void StepDelay(uint t)
{
    uint k;
    while(t--)
    {
        for(k=0; k<80; k++) { ; }   /* 空转循环，产生延时 */
    }
}

/*
 * 函数名: MotorRight
 * 功  能: 步进电机正转一个完整周期（8个相位 = 1步）
 *         按 FFW 相序表依次输出各相的驱动电平，使电机顺时针旋转，
 *         用于打开窗帘。
 *         调用一次此函数电机转动约 5.625°（28BYJ-48 标准步距角除以减速比）。
 */
void MotorRight(void)
{
    uchar i;
    for(i=0; i<8; i++)
    {
        GPIO_MOTOR = FFW[i] & 0x1f; /* 输出当前相位的驱动信号（屏蔽高3位保护其他IO） */
        StepDelay(50);              /* 保持该相位约50个延时单位，再切换到下一相 */
    }
}

/*
 * 函数名: MotorLeft
 * 功  能: 步进电机反转一个完整周期（8个相位 = 1步）
 *         按 FFZ 相序表依次输出各相的驱动电平，使电机逆时针旋转，
 *         用于关闭窗帘。
 * 参  数: time — 每个相位的保持时间（传入 StepDelay 的参数），
 *                数值越大转速越慢，建议与 MotorRight 的延时参数匹配。
 */
void MotorLeft(uint time)
{
    uchar i;
    for(i=0; i<8; i++)
    {
        GPIO_MOTOR = FFZ[i] & 0x1f; /* 输出反转相位的驱动信号 */
        StepDelay(time);            /* 按指定时间保持当前相位 */
    }
}

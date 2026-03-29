/*
 * 文件名: autor.h
 * 功  能: 步进电机驱动头文件
 *         定义电机控制所用的 GPIO 端口宏，并声明驱动函数接口。
 */

#ifndef __AUTOR_H__          /* 防止头文件被重复包含的条件编译开始 */
#define __AUTOR_H__

/* 步进电机驱动信号输出端口，连接到 P0 口 */
#define GPIO_MOTOR P0  

/* 延时函数：用于控制步进电机每相的保持时间（影响转速） */
void StepDelay(unsigned int t);

/* 电机正转一步（顺时针，用于开窗） */
void MotorRight(void);

/* 电机反转一步（逆时针，用于关窗）；time 控制转速 */
void MotorLeft(unsigned int time);

#endif                       /* 防止头文件被重复包含的条件编译结束 */

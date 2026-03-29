#ifndef __AUTOR_H__
#define __AUTOR_H__

#define GPIO_MOTOR P0  

void StepDelay(unsigned int t);
void MotorRight(void);
void MotorLeft(unsigned int time);

#endif
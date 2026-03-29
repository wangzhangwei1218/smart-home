#include <reg52.h>
#include "autor.h"

#define uchar unsigned char
#define uint  unsigned int


uchar code FFW[8] = {0xf1,0xf3,0xf2,0xf6,0xf4,0xfc,0xf8,0xf9};
uchar code FFZ[8] = {0xf9,0xf8,0xfc,0xf4,0xf6,0xf2,0xf3,0xf1};

void StepDelay(uint t)
{
    uint k;
    while(t--)
    {
        for(k=0; k<80; k++) { ; }
    }
}

// ÓÒÐý
void MotorRight(void)
{
    uchar i;
    for(i=0; i<8; i++)
    {
        GPIO_MOTOR = FFW[i] & 0x1f;
        StepDelay(50);
    }
}

// ×óÐý
void MotorLeft(uint time)
{
    uchar i;
    for(i=0; i<8; i++)
    {
        GPIO_MOTOR = FFZ[i] & 0x1f;
        StepDelay(time);
    }
}
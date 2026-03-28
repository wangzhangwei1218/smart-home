#ifndef _DS18B20_H_
#define _DS18B20_H_

#include <reg52.h>
#include <intrins.h> 


#define uchar  unsigned char
#define uint   unsigned int
#define ulong   unsigned long
	
#define NOPS(); { _nop_();_nop_();_nop_();_nop_();}

sbit DQ1=P2^0;

void DS18B20_start_change1();
int DS18B20_read_date1();

#endif

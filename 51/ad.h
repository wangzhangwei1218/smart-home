#ifndef __AD_H__
#define __AD_H__
#include <reg52.h>
#include <intrins.h>

unsigned long dat = 0x00; 

sbit  Clk2= P3^3;
sbit  DATI2=P3^4;
sbit  DATO2= P3^4;  
sbit  CS2= P3^5;
unsigned int  dat2 = 0x00;      //AD值

//AD转换子程序 
unsigned int adc08322(unsigned char CH)
{
  unsigned char i,test,adval;
  adval = 0x00;
  test = 0x00;
   //初始化
  Clk2 = 0;      
  DATI2 = 1;
  _nop_();   _nop_();
  CS2 = 0;
  _nop_();
  Clk2 = 1;
  _nop_();  _nop_();
  //通道选择
 if(CH == 0x00)     
   {
       Clk2 = 0;
       DATI2 = 1;      //通道0的第一位
       _nop_();
       Clk2 = 1;
       _nop_();  _nop_();

       Clk2 = 0;
       DATI2 = 0;      //通道0的第二位
       _nop_();  _nop_();

       Clk2 = 1;
       _nop_();
    } 
    else
    {
       Clk2 = 0;
       DATI2 = 1;      //通道1的第一位
        _nop_();  _nop_();

       Clk2 = 1;
        _nop_();  _nop_();

       Clk2 = 0;
       DATI2 = 1;      //通道1的第二位
      _nop_();
      Clk2 = 1;
      _nop_();
    }
      Clk2 = 0;   _nop_();

      DATI2 = 1;
   for( i = 0;i < 8;i++ )      //读取前8位的值
    {
       _nop_();
       adval <<= 1;
       Clk2 = 1;
       _nop_();  _nop_();

       Clk2 = 0;	   _nop_();

       if (DATO2)
          adval |= 0x01;
      else
          adval |= 0x00;
    }
  for (i = 0; i < 8; i++)      //读取后8位的值
      {
           test >>= 1;
           if (DATO2)
              test |= 0x80;
           else 
              test |= 0x00;
          _nop_();
          Clk2 = 1;
          _nop_();  _nop_();

          Clk2 = 0;   _nop_();

      }
 //比较前8位与后8位的值，如果不相同舍去。若一直出现显示为零，请将该行去掉
  if (adval == test)     
     dat = test;
     _nop_();  _nop_();
     CS2 = 1;        //释放ADC0832
     DATO2 = 1;
     Clk2 = 1;
     return dat;
}

#endif
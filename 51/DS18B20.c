#include<DS18B20.h>

/***
**	延时函数
**   大约11us
***/
void delays(uint x)
{
	while( x-- );
}


/***
**	复位函数
***/
void DS18B20_rst1()
{
	bit flag=1;	     //这个标志位必须置1
	while( flag )
	{
		while( flag )
		{
			DQ1=1;
			delays(1);
			DQ1=0;
			delays(50); //550us
			DQ1=1;
			delays(6);  //66us
			
			flag=DQ1;
		}
		delays(45);   //500us
		flag=~DQ1;
	}
	DQ1=1;
}

/***
**	写一个字节函数
***/
void DS18B20_write_byte1(uchar byte)
{
	uchar i;
	for(i=0;i<8;i++)
	{
		DQ1=1;
		_nop_();
		DQ1=0;
		NOPS();	  //4us
		DQ1=byte&0x01 ;
		delays(6);	//66us
		byte>>=1;
	}
	DQ1=1;
	delays(1);
}

/***
**	读一个字节函数
***/
uchar DS18B20_read_byte1()
{
	uchar i,date=0;
	for(i=0;i<8;i++)
	{
	
		DQ1=1;
		_nop_();
		date>>=1;
		DQ1=0;
		NOPS(); //4us
		DQ1=1;
		NOPS(); //4us
		if(DQ1)
		{
			date |=0x80;
		}
		delays(6); //66us
	}
	DQ1=1;
	
	return date;
}


/***
**	启动温度转换函数
***/
void DS18B20_start_change1()
{
	DS18B20_rst1();
	DS18B20_write_byte1(0xcc);
	DS18B20_write_byte1(0x44);	//发送转换命令	
}

/***
**	读取温度数值函数
***/
int DS18B20_read_date1()
{
	int temp;
	uchar date[2];	      //温度暂存器

	DS18B20_rst1();	      //复位

	DS18B20_write_byte1(0xcc);	  //跳过ROM
	DS18B20_write_byte1(0xbe);	 //发送读温度命令

	date[0]=DS18B20_read_byte1();  //温度低8位
	date[1]=DS18B20_read_byte1();  //温度高8位

	temp=date[1];	   
	temp<<=8;
	temp |= date[0];
	temp>>=4;

	return temp;
}

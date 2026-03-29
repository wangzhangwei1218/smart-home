sbit  RS=P2^5;	//写信号
sbit  RWW=P2^6;	//读信号
sbit  E=P2^7;  //使能信号
#define e1   E=1 
#define rd1  RWW=1  
#define rs1  RS=1  
#define e0   E=0 
#define rd0  RWW=0 
#define rs0  RS=0 
//--------------------------
#define PP P1
//延时函数ms
void _delay_ms(uint t)
{
   uint i,j;
   for(i=0;i<t;i++)
     for(j=0;j<120;j++);
}
//延时函数us
void _delay_us(uchar t)
{
   while(t>0)t--;
} 
//写1602控制字
void  lcd1602_writecrtl(uchar  dat)
{
  rd0;//读信号置0
  rs0;//写信号置0
  _delay_us(5);
  PP=dat;
  e1;//使能信号置1
  _delay_us(5);
  e0;//使能信号置0
}
//写1602数据
void  lcd1602_writenumber(uchar dat)
{
  rd0;//读信号置0
  rs1;//写信号置1
  _delay_us(5);
  PP=dat;
  e1;//使能信号置1
  _delay_us(5);
  e0;//使能信号置0
}
//1602初始化
void  lcd1602_init()  
{
  lcd1602_writecrtl(0x38); //显示模式
  lcd1602_writecrtl(0x06); //显示光标移动位置
  lcd1602_writecrtl(0x0c); //显示开及光标设置
  lcd1602_writecrtl(0x01); //显示清屏
}
//显示地址
void  lcd1602_adr(uchar dat)  
{
  lcd1602_writecrtl(0x80 | dat);

}
//行显示-
void LCD1602_string(uchar hang,uchar lie,uchar const *p)
{
	uchar a;
	if(hang == 1) a = 0x00;
	if(hang == 2) a = 0x40;
	a = a + lie - 1;
	lcd1602_adr(a);
	while(1)
	{
		if(*p == '\0') break;
		lcd1602_writenumber(*p);
		p++;
	}
}
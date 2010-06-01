#include<stdio.h>
#include<stdlib.h>

int main()
{
  FILE* fread = fopen("urlname","r");
  FILE* fwrite = fopen("URLList","w");

  char buffer[100];
  int ran;

  srand(1);
  while(fscanf(fread," %s",buffer) != EOF)
  {
    ran = rand() % 4;

    switch(ran) {
      case 0 :
        fprintf(fwrite,"http://www.%s.com\n",buffer);
        break;
      case 1:
        fprintf(fwrite,"http://www.%s.ca\n",buffer);
        break;
      case 2:
        fprintf(fwrite,"http://www.%s.co.uk\n",buffer);
        break;
      case 3:
        fprintf(fwrite,"http://www.%s.co.kr\n",buffer);
        break;
    }
  }
}

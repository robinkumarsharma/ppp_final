CFLAGS	:=
CFLAGS	+= -std=gnu99
CFLAGS	+= -Wall
CFLAGS	+= -O3
LDLIBS	:= -lm

all: sor-seq sor-par

sor-seq: sor-seq.c
	gcc $(CFLAGS) -o sor-seq sor-seq.c $(LDLIBS)

sor-par: sor-par.c
	mpicc -Wall -o3 sor-par.c -o sor-par $(LDLIBS)

clean:
	-rm -f *.o sor-seq *~ *core
	-rm -f *.o sor-par *~ *core


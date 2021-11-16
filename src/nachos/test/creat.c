/*
 * creat.c
 *
 * Create a file, easy as that
 */

#include "syscall.h"

int main (int argc, char *argv[]) {
    char *str = "myfile.txt";
    create(str);
    return 0;
}
#!/usr/bin/python3

# simple python script that does 1 request to environment netoxena.com and then makes sure it responds adequately

import requests
import time
import curses


def main(stdscr):
    stdscr = curses.initscr()

    curses.noecho()

    curses.cbreak()

    stdscr.keypad(True)
    
    last = time.time() + 1

    stdscr.nodelay(True)
    retry = False
    A = ''
    B = 'NOT FOUND'
    BBB = ''
    BBBB = ''
    while(True):
        time.sleep(.1)
        stdscr.clear()
        stdscr.addstr(10,10,'Last: ' + str(last))
        stdscr.addstr(11,10,'Next: ' + str(time.time()))
        stdscr.addstr(0,3,A)
        stdscr.addstr(1,3,B)
        stdscr.addstr(1,4,BBB)
        stdscr.addstr(1,5,BBBB)
        if ( retry or last + 6 < time.time() ):

            A = str(requests.get('http://netty.netoxena.com/startKmeans/000.png').json()['schedule_uuid'])
            retry = False
            B = 'NOT FOUND'
            BBB = ''
            BBBB = ''
            last = time.time()
        # get res uuid
#            stdscr.addstr(0,3,res)
#            uuid = "0"

    #        time.sleep(1)

        for i in requests.get('http://netty.netoxena.com/getAllSchedules').json():
            if i['schedule_uuid'] == A:
                
                if 'finished' in i['jsonData']:

                    B = 'FOUND' 
                    BBB = str(A) 
                    BBBB = str(i['schedule_uuid'])

        #stdscr.addstr(0,0,str(res))

        stdscr.refresh()

        try:
            c = stdscr.getkey()
            #stdscr.addstr(5,5,str(c))
            if ( c == 'r' ):
                retry = True
            if ( c == 'q' ):
                curses.nocbreak()
                stdscr.keypad(False)
                curses.echo()
                curses.endwin()
                break #exit(0)
        except:
            pass


curses.wrapper(main)

#!/usr/bin/python3

# Integration test - should run continually

import requests
import time
import curses

def main(stdscr):
    sendInterval = 9.0

    scheduled = {}

    stdscr = curses.initscr()

    curses.noecho()

    curses.cbreak()
    
    curses.start_color()

    curses.init_pair(1, curses.COLOR_GREEN, curses.COLOR_BLACK)
#     curses.init_pair(2, curses.COLOR_GREEN, curses.COLOR_BLACK)

    stdscr.keypad(True)
    
    last = time.time() + 1

    stdscr.nodelay(True)

    while(True):
        stdscr.clear()

        printdictA = sorted(scheduled, key=lambda _: (scheduled[_]['timeStart']))
        printdictA.reverse()

        for n in range(0,min(len(printdictA),stdscr.getmaxyx()[0])):
            stdscr.addstr(0 + n, 60,
                str(printdictA[n]) +
                ': TimeStart:' +
                str(scheduled[printdictA[n]]['timeStart']) +
                ': TimeEnd:' +
                str(scheduled[printdictA[n]]['timeEnd']) +
                ': TimeToComplete:' +
                str(scheduled[printdictA[n]]['timeToComplete']))
 

        time.sleep(1)
        stdscr.addstr(8,10,'Last: ' + str(last))
        stdscr.addstr(9,10,'Next: ' + str(last + sendInterval))
        stdscr.addstr(10,10,'Current: ' + str(time.time()))

        stdscr.addstr(1,3,'Request Interval In Seconds: ' + str(sendInterval), curses.color_pair(1))
        stdscr.addstr(3,3,'Use + and - keys, q to exit.')

        if ( last + sendInterval < time.time() ):

            try:
                A = str(requests.get('http://netty.netoxena.com/startKmeans/013.png').json()['schedule_uuid'])
            except:
                pass

            scheduledEntry = {}
            scheduledEntry['timeStart'] = time.time()
            scheduledEntry['timeEnd'] = None
            scheduledEntry['timeToComplete'] = None
            scheduled[A] = scheduledEntry
            last = time.time()
        try:
            for i in requests.get('http://netty.netoxena.com/getAllSchedules').json():
                if i['schedule_uuid'] in scheduled:
                    if 'True' == str('finished' in i['jsonData']):
                        if (scheduled[i['schedule_uuid']]['timeEnd'] == None):
                            scheduled[i['schedule_uuid']]['timeEnd'] = time.time()
                            scheduled[i['schedule_uuid']]['timeToComplete'] = scheduled[i['schedule_uuid']]['timeEnd'] - scheduled[i['schedule_uuid']]['timeStart']
        except:
            pass

        stdscr.refresh()

        try:
            c = stdscr.getkey()
            if ( c == '+'):
                sendInterval += 0.5
            if ( c == '-'):
                sendInterval -= 0.5
            if ( c == 'q' ):
                curses.nocbreak()
                stdscr.keypad(False)
                curses.echo()
                curses.endwin()
                break #exit(0)
        except:
            pass


curses.wrapper(main)

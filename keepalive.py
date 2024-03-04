#!/usr/bin/python3

# Integration test - should run continually

import requests
import time
import curses

def main(stdscr):

    scheduled = {}

    stdscr = curses.initscr()

    curses.noecho()

    curses.cbreak()
    
    curses.start_color()

    curses.init_pair(1, curses.COLOR_RED, curses.COLOR_BLACK)
    curses.init_pair(2, curses.COLOR_GREEN, curses.COLOR_BLACK)

    stdscr.keypad(True)
    
    last = time.time() + 1

    stdscr.nodelay(True)
    retry = False
    A = ''
    B = 'NOT FOUND'
    BBB = ''
    BBBB = ''
    while(True):
        stdscr.clear()

        printdictA = sorted(scheduled, key=lambda _: (scheduled[_]['timeStart']))
        printdictA.reverse()
        #stdscr.addstr(13,13,'asdf') # str(printdictA))
        for n in range(0,min(len(printdictA),30)):
            stdscr.addstr(0 + n, 60,
                str(printdictA[n]) +
                ': TimeStart:' +
                str(scheduled[printdictA[n]]['timeStart']) +
                ': TimeEnd:' +
                str(scheduled[printdictA[n]]['timeEnd']) +
                ': TimeToComplete:' +
                str(scheduled[printdictA[n]]['timeToComplete']))
 

        time.sleep(1)
        stdscr.addstr(10,10,'Last: ' + str(last))
        stdscr.addstr(11,10,'Next: ' + str(time.time()))
        stdscr.addstr(0,3,A)
        stdscr.addstr(1,3,B, curses.color_pair(1))
        stdscr.addstr(1,4,BBB)
        stdscr.addstr(1,5,BBBB)
        if ( retry or last + 9 < time.time() ):

            try:
                A = str(requests.get('http://netty.netoxena.com/startKmeans/000.png').json()['schedule_uuid'])
            except:
                pass

            scheduledEntry = {}
            scheduledEntry['timeStart'] = time.time()
            scheduledEntry['timeEnd'] = None
            scheduledEntry['timeToComplete'] = None
            scheduled[A] = scheduledEntry
            retry = False
            B = 'NOT FOUND'
            BBB = ''
            BBBB = ''
            last = time.time()
        # get res uuid
#            stdscr.addstr(0,3,res)
#            uuid = "0"

    #        time.sleep(1)
        try:
            for i in requests.get('http://netty.netoxena.com/getAllSchedules').json():
                #stdscr.addstr(7, 55, str('finished' in i['jsonData']))  # ucla rocneck you want buy above the neck surgery? california lobe clear you want buy missile
                # we are insured using our 6 million investors chronic
                #stdscr.addstr(8, 56, str(i['schedule_uuid'] in scheduled))
                #stdscr.addstr(9, 57, str(i['schedule_uuid']))
                #stdscr.addstr(10, 58, str(scheduled.keys()))
                if i['schedule_uuid'] in scheduled:

                    #stdscr.addstr(11, 59, 'asdf')#json.loads(i['jsonData']))
                    #scheduled[i['schedule_uuid']]['timeEnd'] = 111
                    if 'True' == str('finished' in i['jsonData']): # 'finished' in i['jsonData']:    # == json.loads(i['jsonData'])['status']:
                        #stdscr.addstr(13, 60, i['jsonData'])
                        #stdscr.addstr(14, 61, scheduled[i['schedule_uuid']])
                        #stdscr.addstr(15, 62, scheduled[i['schedule_uuid']]['timeEnd'])
                        if (scheduled[i['schedule_uuid']]['timeEnd'] == None):
                            scheduled[i['schedule_uuid']]['timeEnd'] = time.time()

                            scheduled[i['schedule_uuid']]['timeToComplete'] = scheduled[i['schedule_uuid']]['timeEnd'] - scheduled[i['schedule_uuid']]['timeStart']

                        #B = 'FOUND'
                        #BBB = str(A)
                        #BBBB = str(i['schedule_uuid'])
        except:
            pass
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

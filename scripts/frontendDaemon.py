#!/usr/bin/env python

import sys, time
import urllib2, os, subprocess, signal
from daemon import Daemon

class FrontendDaemon(Daemon):
  time = 0

  def startWebsite(self):
    # remove the running_pid
    try:
      os.remove("/etc/elementary/frontend/RUNNING_PID")
    except OSError, e:
      print ("Error: %s - %s." % (e.filename,e.strerror))

    # execute the play server
    try:
      subprocess.Popen(["/etc/elementary/frontend/bin/elementary-frontend", "-J-Xms512M", "-J-Xmx2048M", "-J-XX:MaxPermSize=2048M", "-Dhttp.port=4242"])
      time = 0
    except Exception, e:
      print "Could not restart website (" + str(e) + ")..."

  def run(self):
    print "Starting the daemon"
    while True:
      try:
        urllib2.urlopen('http://localhost:4242/')
      except urllib2.HTTPError, e:
        print "Code Error: " + str(e.code)
        self.startWebsite()
      except urllib2.URLError, e:
        print "Args Error: " + str(e.args)
        self.startWebsite()
      time.sleep(30000)
      time = time + 30000

      # Check that the site is not running longer than one week
      if (time > (7 * 24 * 3600000) and datetime.datetime.now().hour == 2):
        self.restart()

  def clean(self):
    try:
      fo = open("/etc/elementary/frontend/RUNNING_PID", 'r')
      pid = fo.read()
      fo.close()
      os.kill(int(pid), signal.SIGTERM)
    except Exception, e:
      print ("Could not kill site: %s" % e)

if __name__ == "__main__":
  daemon = FrontendDaemon("/tmp/frontendDaemon.pid")
  if len(sys.argv) == 2:
      if 'start' == sys.argv[1]:
          daemon.start()
      elif 'stop' == sys.argv[1]:
          daemon.stop()
      elif 'restart' == sys.argv[1]:
          daemon.restart()
      else:
          print "Unknown command"
          sys.exit(2)
          sys.exit(0)
  else:
      print "Usage: %s start|stop|restart" % sys.argv[0]
      sys.exit(2)

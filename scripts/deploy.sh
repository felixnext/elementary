#!/bin/bash
# Deployment of Elementary-Framework

LOG_DIR=/home/git/deploy_logs
LOG_FILE=$LOG_DIR/deploy_$(date +"%Y-%m-%d-%H%M").log
REPO_DIR=/home/git/repositories/elementary_base.git
PULL_DIR=/home/git/deploy_repo

QAPI_USER=elementary
QAPI_SERVER=is61.idb.cs.tu-bs.de
QAPI_DIR=/home/elementary/elementary
QAPI_URL=$QAPI_USER@$QAPI_SERVER:$QAPI_DIR/

PLINE_USER=elementary
PLINE_SERVER=is61.idb.cs.tu-bs.de
PLINE_DIR=/home/elementary/elementary
PLINE_URL=$PLINE_USER@$PLINE_SERVER:$PLINE_DIR/

STATS_USER=elementary
STATS_SERVER=is61.idb.cs.tu-bs.de
STATS_DIR=/home/elementary/elementary
STATS_URL=$STATS_USER@$STATS_SERVER:$STATS_DIR/

#functions
function checkExitCode {
	if [ $1 -ne "0" ]
	then
		echo "$2 ERROR - aborting deployment." | tee -a $LOG_FILE
		exit 1
	fi
}

#stop services
echo "Stopping services..." | tee -a $LOG_FILE
ssh -t -T $PLINE_USER@$PLINE_SERVER 'sudo /etc/init.d/pipeline stop' | tee -a $LOG_FILE
ssh -t -T $QAPI_USER@$QAPI_SERVER 'sudo /etc/init.d/questionapi stop' | tee -a $LOG_FILE
ssh -t -T $STATS_USER@$STATS_SERVER 'sudo /etc/init.d/statistics stop' | tee -a $LOG_FILE

#main
echo "Deploying Elementary..." | tee -a $LOG_FILE

# update deployment clone
if [ ! -d $PULL_DIR ]
then
	mkdir $PULL_DIR | tee -a $LOG_FILE
	cd $PULL_DIR
	env -i git clone $REPO_DIR $PULL_DIR | tee -a $LOG_FILE
else
	cd $PULL_DIR
	env -i git -C $PULL_DIR pull | tee -a $LOG_FILE
fi

echo "Compiling..." | tee -a $LOG_FILE
(cd $PULL_DIR && sbt compile) | tee -a $LOG_FILE
checkExitCode ${PIPESTATUS[0]} "COMPILATION"

#test (optionally)
#echo "Testing..." | tee -a $LOG_FILE
#sbt test | tee -a $LOG_FILE
#GcheckExitCode ${PIPESTATUS[0]} "TESTING"

#assembly
echo "Assembly..." | tee -a $LOG_FILE
(cd $PULL_DIR && sbt assembly) | tee -a $LOG_FILE
checkExitCode ${PIPESTATUS[0]} "ASSEMBLY"
(cd $PULL_DIR && sbt assemblyPackageDependency) | tee -a $LOG_FILE
checkExitCode ${PIPESTATUS[0]} "ASSEMBLY PACKAGE DEPENDENCY"

#distribute
scp $PULL_DIR/deploy/question-api* $QAPI_URL
ssh -t $QAPI_USER@$QAPI_SERVER 'sudo /etc/init.d/questionapi start;sleep 5' | tee -a $LOG_FILE

scp $PULL_DIR/deploy/statistics* $STATS_URL
ssh -t $STATS_USER@$STATS_SERVER 'sudo /etc/init.d/statistics start;sleep 5' | tee -a $LOG_FILE


scp $PULL_DIR/deploy/pipeline* $PLINE_URL
ssh -t $PLINE_USER@$PLINE_SERVER 'sudo /etc/init.d/pipeline start;sleep 5' | tee -a $LOG_FILE

echo "Successfully deployed Elementary!"

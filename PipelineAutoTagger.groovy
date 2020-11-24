import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput;

properties([parameters([string(defaultValue: 'Develop', description: 'Enter the git branch name', name: 'Branch', trim: false), string(defaultValue: '', description: 'Enter account number', name: 'account', trim: true), string(defaultValue: 'us-east-1', description: 'enter region', name: 'region', trim: false), choice(choices: ['SSM_PARAMETER', 'CLOUDWATCH_LOGS'], description: 'Select the specific remediation command', name: 'command'), booleanParam(defaultValue: true, description: 'Set to true to display results of the remediation plan without taking any action', name: 'preview')])])
node ('CPE') {
    stage('SCM') {
        //set up proxy
        env.http_proxy = 'http://proxy.us-east-1.appqa.dtcc.org:8080'
        env.https_proxy = 'http://proxy.us-east-1.appqa.dtcc.org:8080'
        env.NO_PROXY   = '169.254.169.254,repo.dtcc.com,idbd.dtcc.com'
        deleteDir()
                git url: 'ssh://git@code.dtcc.com:7999/cpe/ccoe-remediation-app.git',branch: "${branch}"
                WORKSPACE  = env.WORKSPACE
                env.account ="${params.account}"
                env.region = "${params.region}"
                println("AWS Account: ${params.account}")
          assumeRole("${params.account}","Ops-automation-role",43200) 
          sh '''
            python3 -m venv venv
            source venv/bin/activate
            cd src
            which python3
            python -m pip -q install -r requirements.txt
            if [ "${preview} == "true"]
            then
                echo "running in previeew mode"
                python autotagger.py -command ${command} -region ${region} -preview
            else
                echo "running in remediation mode"
                python autotagger.py -command ${command} -region ${region}
            fi
          '''
    }
}
def assumeRole(String accountId,String roleName,Integer durationSeconds) {
    assumeRoleARN = "arn:aws:iam::$accountId:role/$roleName"
    String assumeRoleResponseJson = sh returnSdout: true , script: "aws sts assume-role --duration-seconds 43200 --role-arn $assumeRoleARN --role-session-name  tsmci-jenkins-session"
    assumeRoleResponse = new JsonSlurperClassic().parseText(assumeRoleResponseJson)
    env.AWS_ACCESS_KEY_ID = assumeRoleResponse.Credentials.AccesskeyId
    env.AWS_SECRET_ACCESS_KEY assumeRoleResponse.Credentials.SecretAccessKey
    env.AWS_SESSION_TOKEN =assumeRoleResponse.Credentials.SessionToken
    env.AWS_DEFAULT_REGION ='us-east-1'
}

import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root=fileURLToPath(new URL('..',import.meta.url));
for(const key of ['DB_URL','DB_USER','DB_PASSWORD','DB_MIGRATION_USER','DB_MIGRATION_PASSWORD','GATEWAY_SECRET','APP_ORIGINS','BOOTSTRAP_ADMIN_EMAIL','BOOTSTRAP_ADMIN_PASSWORD']) {
  if(!process.env[key])throw new Error('Missing required server configuration: '+key);
}
if(!process.env.DB_URL.startsWith('jdbc:sqlserver://'))throw new Error('This service requires SQL Server');
const env={
  ...process.env,
  API_BIND:'127.0.0.1',
  API_PORT:'8088',
  API_ORIGIN:'http://127.0.0.1:8088',
  GATEWAY_HOST:'0.0.0.0',
  GATEWAY_PORT:process.env.PORT||'10000',
  COOKIE_SECURE:'true',
  MEDIA_ROOT:process.env.MEDIA_ROOT||'/tmp/life-trick-media',
  FFMPEG_PATH:'/usr/bin/ffmpeg',
  FFPROBE_PATH:'/usr/bin/ffprobe',
  DB_POOL_MAX_SIZE:process.env.DB_POOL_MAX_SIZE||'3',
  DB_POOL_MIN_IDLE:process.env.DB_POOL_MIN_IDLE||'0',
  DB_CONNECTION_TIMEOUT_MS:process.env.DB_CONNECTION_TIMEOUT_MS||'60000',
  DB_IDLE_TIMEOUT_MS:process.env.DB_IDLE_TIMEOUT_MS||'60000',
  DB_MAX_LIFETIME_MS:process.env.DB_MAX_LIFETIME_MS||'300000',
};
let stopping=false;
const children=[];
function stop(code=0){if(stopping)return;stopping=true;for(const child of children)child.kill('SIGTERM');setTimeout(()=>{for(const child of children)child.kill('SIGKILL');process.exit(code);},8000).unref();}
function launch(command,args,cwd){const child=spawn(command,args,{cwd,env,stdio:'inherit'});children.push(child);child.once('error',()=>{console.error('Unable to start a required service');stop(1);});child.once('exit',code=>{if(!stopping)stop(code||1);});return child;}
process.on('SIGTERM',()=>stop());process.on('SIGINT',()=>stop());
launch(path.join(process.env.JAVA_HOME||'/opt/java/openjdk','bin/java'),['-Xms32m','-Xmx192m','-XX:MaxMetaspaceSize=128m','-XX:ReservedCodeCacheSize=48m','-Xss512k','-Djava.awt.headless=true','-jar','app.jar'],path.join(root,'BE'));
launch(process.execPath,['--max-old-space-size=64','src/server.js'],path.join(root,'NodeGateway'));

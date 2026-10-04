import pathlib,subprocess,time,json,re,sys
root=pathlib.Path(sys.argv[1]); adb=['adb']
def shell(*args):return subprocess.check_output(adb+['shell',*args],text=True,stderr=subprocess.STDOUT)
configs=[('cainiao','com.cainiao.wireless','com.cainiao.wireless.homepage.view.activity.WelcomeActivity','com.cainiao.wireless.homepage.view.activity.HomePageActivity')]
last=None
for app,pkg,normal,candidate in configs:
 for pair in range(1,4):
  for kind,component in [('normal',normal),('candidate',candidate)]:
   if last is not None:
    delay=70-(time.monotonic()-last)
    if delay>0:time.sleep(delay)
   shell('input','keyevent','KEYCODE_WAKEUP')
   screen=shell('dumpsys','window')
   if 'mDreamingLockscreen=true' in screen:raise SystemExit('LOCKED: stop')
   shell('am','force-stop',pkg);shell('input','keyevent','KEYCODE_HOME');time.sleep(2)
   folder=root/f'{app}-{pair}-{kind}';folder.mkdir(exist_ok=True)
   started=time.monotonic();last=started
   command=['am','start','-W','-n',pkg+'/'+component,'-a','android.intent.action.MAIN','-f','0x10200000']
   if kind=='normal':command+=['-c','android.intent.category.LAUNCHER']
   proc=subprocess.Popen(adb+['shell',*command],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
   samples=[]
   for offset in [0.35,0.85,1.6,3,5,8,12]:
    delay=offset-(time.monotonic()-started)
    if delay>0:time.sleep(delay)
    image=subprocess.check_output(adb+['exec-out','screencap','-p']);(folder/f'{offset}.png').write_bytes(image)
    state=shell('dumpsys','activity','activities')
    samples.append({'elapsed':round(time.monotonic()-started,2),'activity':[l.strip() for l in state.splitlines() if 'mResumedActivity' in l]})
   launch=proc.communicate(timeout=20)[0]
   record={'app':app,'pair':pair,'kind':kind,'wall_time':time.strftime('%Y-%m-%dT%H:%M:%S%z'),'launch':launch,'samples':samples}
   (folder/'result.json').write_text(json.dumps(record,ensure_ascii=False,indent=2))
   print(json.dumps(record,ensure_ascii=False),flush=True)
   shell('input','keyevent','KEYCODE_HOME')

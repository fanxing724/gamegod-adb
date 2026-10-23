#!/usr/bin/env python3
"""Fetch the pinned official SDK archives used by this project into a task-local directory."""
import hashlib,os,urllib.request,zipfile
from pathlib import Path
work=Path(os.environ.get('STARPLAY_WORK','/home/fanxing/codex-workspace/game-assistant'))
target=Path(os.environ.get('STARPLAY_SDK',str(work/'toolchain/sdk')))
cache=work/'toolchain';cache.mkdir(parents=True,exist_ok=True);target.mkdir(parents=True,exist_ok=True)
packages=[('platform-35_r02.zip','0bb560a90a7a2cbd0dd8348224d518b638fe7949'),('build-tools_r35_linux.zip','2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc')]
for name,sha1 in packages:
 p=cache/name
 if not p.exists():
  print('Downloading official Android archive:',name)
  with urllib.request.urlopen('https://dl.google.com/android/repository/'+name,timeout=90) as response,p.open('wb') as out:
   while block:=response.read(1024*1024):out.write(block)
 if hashlib.sha1(p.read_bytes()).hexdigest()!=sha1:raise RuntimeError('SDK checksum mismatch: '+name)
 with zipfile.ZipFile(p) as z:
  for item in z.infolist():
   out=(target/item.filename).resolve()
   if not out.is_relative_to(target.resolve()):raise RuntimeError('Unsafe archive path')
  z.extractall(target)
for name in ['aapt','aapt2','zipalign','d8','apksigner']:
 for p in target.glob('android-*/'+name):p.chmod(0o755)
print('SDK ready:',target)

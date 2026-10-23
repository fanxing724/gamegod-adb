#!/usr/bin/env python3
"""Small reproducible native Android build; official SDK only, no Gradle or runtime libraries."""
from pathlib import Path
import os,subprocess,zipfile,hashlib,json
ROOT=Path(__file__).resolve().parent
VERSION='0.2.0'
WORK=Path(os.environ.get('STARPLAY_WORK','/home/fanxing/codex-workspace/game-assistant'))
SDK=Path(os.environ.get('STARPLAY_SDK',str(WORK/'toolchain/sdk')))
BUILD=WORK/'build';OUT=WORK/'releases';KEYS=WORK/'signing'
for d in [BUILD,OUT,KEYS]:d.mkdir(parents=True,exist_ok=True)
KEYS.chmod(0o700)
TOOLS=next(SDK.glob('android-*/aapt2')).parent
ANDROID=next(SDK.rglob('android.jar'))
def run(*args):subprocess.run([str(a) for a in args],check=True,cwd=ROOT)
# Verify behavior of the durable mode transaction independently of Android.
tests=BUILD/'tests';tests.mkdir(exist_ok=True)
run('javac','--release','8','-d',tests,ROOT/'app/src/main/java/cn/fanxing/starplay/ModeSession.java',ROOT/'tests/ModeSessionTest.java',ROOT/'app/src/main/java/cn/fanxing/starplay/MonitorMath.java',ROOT/'tests/MonitorMathTest.java')
run('java','-cp',tests,'ModeSessionTest')
run('java','-cp',tests,'MonitorMathTest')
res=BUILD/'resources.zip'
run(TOOLS/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',res)
gen=BUILD/'generated';gen.mkdir(exist_ok=True)
base=BUILD/'base.apk'
run(TOOLS/'aapt2','link','-I',ANDROID,'--manifest',ROOT/'app/src/main/AndroidManifest.xml','--java',gen,'-o',base,res)
classes=BUILD/'classes';classes.mkdir(exist_ok=True)
sources=list((ROOT/'app/src/main/java').rglob('*.java'))+list(gen.rglob('*.java'))
run('javac','--release','8','-encoding','UTF-8','-classpath',ANDROID,'-d',classes,*sources)
jar=BUILD/'app.jar'
run('jar','--create','--file',jar,'-C',classes,'.')
dex=BUILD/'dex';dex.mkdir(exist_ok=True)
run('java','-Xmx768m','-cp',TOOLS/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','28','--lib',ANDROID,'--output',dex,jar)
unsigned=BUILD/'unsigned.apk'
with zipfile.ZipFile(base) as src,zipfile.ZipFile(unsigned,'w',zipfile.ZIP_DEFLATED) as dst:
 for entry in src.infolist():dst.writestr(entry,src.read(entry.filename))
 dst.write(dex/'classes.dex','classes.dex')
aligned=BUILD/'aligned.apk'
run(TOOLS/'zipalign','-f','-p','4',unsigned,aligned)
key=KEYS/'starplay-local.p12'
if not key.exists():
 run('keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass','starplay-local-build','-keypass','starplay-local-build','-alias','starplay','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Fanxing Starplay, OU=Local Apps, O=Fanxing, C=CN')
 key.chmod(0o600)
apk=OUT/('starplay-'+VERSION+'.apk')
run('java','-jar',TOOLS/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','starplay','--ks-pass','pass:starplay-local-build','--out',apk,aligned)
run('java','-jar',TOOLS/'lib/apksigner.jar','verify','--verbose',apk)
run(TOOLS/'zipalign','-c','-p','4',apk)
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
(OUT/('starplay-'+VERSION+'.apk.sha256')).write_text(sha+'  '+apk.name+'\n')
print('APK:',apk,'\nSHA256:',sha,'\nBytes:',apk.stat().st_size)

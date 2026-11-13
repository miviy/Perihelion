#!/usr/bin/env python3
"""Pin missing Gradle metadata only after comparing cache bytes with the publisher repository."""
from pathlib import Path
import concurrent.futures,hashlib,re,urllib.request,subprocess
root=Path(__file__).resolve().parents[1]
reports=list((root/'build/reports/dependency-verification').glob('*/dependency-verification-report.html'))
report=max(reports,key=lambda p:p.stat().st_mtime).read_text()
paths=list(dict.fromkeys(re.findall(r'href="file:([^\"]+/caches/modules-2/files-2\.1/[^\"]+)"',report)))
def verify(name):
 p=Path(name); group,artifact,version,_,filename=p.parts[-5:]
 base='https://maven.minecraftforge.net/' if group.startswith(('net.minecraftforge','cpw.mods')) else 'https://repo.maven.apache.org/maven2/'
 url=base+group.replace('.','/')+'/'+artifact+'/'+version+'/'+filename
 data=subprocess.check_output(["curl","--fail","--silent","--show-error","--location","--max-time","40",url])
 if data!=p.read_bytes():raise RuntimeError('Publisher/cache mismatch: '+filename)
 return group,artifact,version,filename,hashlib.sha256(data).hexdigest(),url
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: verified=list(pool.map(verify,paths))
p=root/'gradle/verification-metadata.xml';text=p.read_text()
for group,artifact,version,filename,checksum,url in verified:
 element=f'         <artifact name="{filename}">\n            <sha256 value="{checksum}" origin="Verified publisher bytes 2026-10-17"/>\n         </artifact>\n'
 pattern=r'(<component group="'+re.escape(group)+r'" name="'+re.escape(artifact)+r'" version="'+re.escape(version)+r'">)(.*?)(</component>)'
 m=re.search(pattern,text,re.S)
 if m:
  if '<artifact name="'+filename+'">' in m[2]:continue
  text=text[:m.end(1)]+'\n'+element.rstrip()+text[m.end(1):]
 else:text=text.replace('   </components>',f'      <component group="{group}" name="{artifact}" version="{version}">\n{element}      </component>\n   </components>')
p.write_text(text)
print('Verified and pinned',len(verified),'publisher artifacts.')

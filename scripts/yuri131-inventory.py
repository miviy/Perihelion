#!/usr/bin/env python3
"""Record and verify the complete fixed Yuri1.3.1 module inventory; coverage is not runtime parity."""
from pathlib import Path
import hashlib,json,re,subprocess,sys
root=Path(__file__).resolve().parents[1]
donor=root.parent/'donor_clients/Yuri'
revision='1cb86ad94ce1f01aa9b715577163c8ecc47b7def'
assert subprocess.check_output(['git','-C',str(donor),'rev-parse','HEAD'],text=True).strip()==revision
base=donor/'src/main/java/ddlc/yuri/modules/impl'
previous=json.loads((root/'docs/yuri-latest-modules.json').read_text())['modules']
by_stem={Path(name).name:entry for name,entry in previous.items()}
new_ids={'Displace':'displace','Basketball':'basketball','Pool':'pool','BedWarsUtils':'bedwarsutility','CustomMinecraft':None,'Horns':'horns','CatEars':'catears','ArmorRatio':'armorratio','Reach':'reach','BlockIn':'blockin','AutoClicker':'autoclicker','KeepSprint':'keepsprint','TriggerBot':'triggerbot','BlockHit':'blockhit','AimAssist':'aimassist','Clutch':'clutch','HitboxExpand':'hitboxexpand','InvMove':'invmove','AutoServer':'autoserver'}
source='\n'.join(p.read_text() for p in (root/'forge/src/main/java/perihelion/integration/minecraft189/module').rglob('*.java'))
ids=set(re.findall(r'super\(\s*(?:r|runtime),\s*"([a-z0-9]+)"',source))
ids.update(re.findall(r'new ModuleInfo\(\s*"([a-z0-9]+)"',source))
rows={}
changed=set(subprocess.check_output(['git','-C',str(donor),'diff','--name-only','50d7322..HEAD'],text=True).splitlines())
for path in sorted(base.glob('*/*Module.java')):
 name=str(path.relative_to(base));stem=path.stem.removesuffix('Module')
 old=previous.get(name,by_stem.get(path.name,{}))
 native=new_ids.get(stem,old.get('native'))
 status='native-implementation' if native else old.get('status','excluded-client-ui')
 if native:assert native in ids,(name,native)
 rel=str(path.relative_to(donor))
 rows[name]={'status':status,'native':native,'donor_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'donor_git_blob':subprocess.check_output(['git','-C',str(donor),'rev-parse','HEAD:'+rel],text=True).strip(),'changed_since_previous_audit':rel in changed,'verification':'See YURI_131_IMPLEMENTATION.md and module notes. Source coverage alone is not identical-runtime or server proof.'}
result={'revision':revision,'prior_audit_revision':'50d7322b85cb8de096241077ca3130e9e9715d20','module_count':len(rows),'classification':{status:sum(e['status']==status for e in rows.values()) for status in sorted({e['status'] for e in rows.values()})},'removed_since_previous_audit':sorted(set(previous)-set(rows)),'modules':rows}
path=root/'docs/yuri-1.3.1-modules.json'
if '--write' in sys.argv:path.write_text(json.dumps(result,indent=2)+'\n')
else:assert json.loads(path.read_text())==result,'Inventory changed; inspect before regenerating'
print(json.dumps({'revision':revision,'count':len(rows),'classification':result['classification']}))

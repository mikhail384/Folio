"""Copy only the tool-art PNGs from the user's locally installed Freenotes APK, byte for byte."""
from pathlib import Path
import argparse,hashlib,json,zipfile,struct

ROOT=Path(__file__).resolve().parents[1]
FAMILIES={
    'ball':['ic_ball_pen_part_1_night.png','ic_ball_pen_part_2_night.png','ic_ball_pen_part_3_night.png','ic_ball_pen_part_4_night.png','ic_ball_pen_highlighter_night.png'],
    'fountain':['ic_pen_part_1.png','ic_pen_part_2_night.png','ic_pen_part_3_night.png','ic_pen_part_4_night.png','ic_pen_highlighter_night.png'],
    'pencil':['ic_pencil_part_1.png','ic_pencil_part_2_night.png','ic_pencil_part_3_night.png','ic_pencil_part_4_night.png'],
    'marker':['ic_highlighter_part_1_night.png','ic_highlighter_part_2_night.png','ic_highlighter_part_3_night.png','ic_highlighter_part_4_night.png','ic_highlighter_pen_highlighter_night.png'],
    'felt':['ic_underline_pen_part_1.png','ic_underline_pen_part_2_night.png','ic_underline_pen_part_3.png','ic_underline_pen_part_4_night.png'],
    'eraser':['ic_eraser_part_1_night.png','ic_eraser_part_2_night.png','ic_eraser_part_2_night_cha.png','ic_eraser_part_3_night.png','ic_eraser_highlighter_night.png'],
    'laser':['ic_laser_pen_head1.png','ic_laser_pen_head2.png','ic_laser_pen_head_night.png','ic_laser_pen_foot_night.png','ic_laser_highlighter_night.png'],
    'lasso':['ic_lasso_part_1_night.png','ic_lasso_part_2_night.png','ic_lasso_highlighter_night.png'],
    'flat':['ic_tape_25.png','ic_brush_ruler_night.png'],
}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('apk',type=Path);args=parser.parse_args()
    output=ROOT/'app/src/main/assets/freenotes_tools';output.mkdir(parents=True,exist_ok=True)
    manifest={'sourcePackage':'ai.note.notepad.memo.journal.freenote','sourceApkSha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),'scope':'original tool-art PNGs only, from local APK; no source code, accounts, ads, fonts or native libraries copied','families':FAMILIES,'files':[]}
    with zipfile.ZipFile(args.apk) as archive:
        for name in sorted({n for values in FAMILIES.values() for n in values}):
            matches=[n for n in archive.namelist() if n.startswith('res/') and Path(n).name==name];assert len(matches)==1,(name,matches)
            path=matches[0];data=archive.read(path);assert data[:8]==b'\x89PNG\r\n\x1a\n';width,height=struct.unpack('>II',data[16:24]);(output/name).write_bytes(data)
            manifest['files'].append({'file':name,'sourcePath':path,'sha256':hashlib.sha256(data).hexdigest(),'width':width,'height':height,'bytes':len(data)})
    (output/'source.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Copied',len(manifest['files']),'original PNGs;',sum(x['bytes'] for x in manifest['files']),'bytes')

if __name__=='__main__':main()

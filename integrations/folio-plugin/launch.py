"""Portable entry point. Setup installs a private Python environment in the user data folder."""
from pathlib import Path
import os,sys
root=Path(__file__).resolve().parent
data=Path(os.environ.get('FOLIO_DATA',str(Path.home()/'.folio-plugin')))
python=data/'runtime'/('Scripts/python.exe' if os.name=='nt' else 'bin/python')
if not python.is_file():
    print('Folio plugin: run setup.py first.',file=sys.stderr);sys.exit(1)
os.execv(str(python),[str(python),str(root/'server.py'),*sys.argv[1:]])

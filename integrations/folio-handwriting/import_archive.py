"""Import a password-protected Folio training archive for local GPU training.

The password is requested interactively and is never a command-line argument.
No plaintext archive is written; reviewed examples are stored in the private
Folio corpus as individual files after validating the complete input.
"""
import argparse
import base64
import getpass
import json
from pathlib import Path
import re
import sys

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'folio-plugin'))
from corpus import Corpus,prepare

FORMAT='folio-training-portable-v1'


def decode(value):
    if not isinstance(value,str) or not re.fullmatch(r'[A-Za-z0-9_-]+',value):raise ValueError('Повреждён файл')
    return base64.urlsafe_b64decode(value+'='*((-len(value))%4))


def decrypt(raw,password):
    if len(raw)>40*1024*1024 or not isinstance(password,str) or not 8<=len(password)<=512:raise ValueError('Недопустимый файл или пароль')
    envelope=json.loads(raw);iterations=envelope.get('iterations')
    if envelope.get('format')!=FORMAT or envelope.get('kdf')!='PBKDF2-SHA256' or type(iterations) is not int or not 210000<=iterations<=600000:raise ValueError('Неподдерживаемый файл')
    salt,iv,ciphertext=(decode(envelope.get(k)) for k in ('salt','iv','ciphertext'))
    if len(salt)!=16 or len(iv)!=12 or len(ciphertext)<16:raise ValueError('Повреждён файл')
    key=PBKDF2HMAC(algorithm=hashes.SHA256(),length=32,salt=salt,iterations=iterations).derive(password.encode('utf-8'))
    try:plaintext=AESGCM(key).decrypt(iv,ciphertext,FORMAT.encode('utf-8'))
    except Exception as e:raise ValueError('Неверный пароль или повреждён файл') from e
    if len(plaintext)>24*1024*1024:raise ValueError('Файл слишком большой')
    items=json.loads(plaintext)
    if not isinstance(items,list) or not 1<=len(items)<=10000:raise ValueError('Нет образцов или их слишком много')
    clean=[]
    for item in items:
        if not isinstance(item,dict) or item.get('format')!='folio-training-example-v1':raise ValueError('Повреждён образец')
        prepared=prepare(item.get('text'),item.get('strokes'),item.get('kind'),item.get('source'),item.get('document_id'),item.get('page_id'))
        if prepared!=item:raise ValueError('Неожиданные данные в образце')
        clean.append(prepared)
    return clean


def main():
    parser=argparse.ArgumentParser();parser.add_argument('archive');parser.add_argument('--profile',default='my-handwriting');parser.add_argument('--corpus',type=Path,default=Path.home()/'.folio-plugin'/'corpus');args=parser.parse_args()
    items=decrypt(Path(args.archive).read_bytes(),getpass.getpass('Пароль файла: '));corpus=Corpus(args.corpus,args.profile)
    added=sum(corpus.add(i['text'],i['strokes'],i['kind'],i['source'],i['document_id'],i['page_id']) for i in items)
    print('Импортировано новых проверенных примеров:',added,'; дубликаты пропущены.')


if __name__=='__main__':main()

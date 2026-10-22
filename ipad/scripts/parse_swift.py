"""Optional Swift grammar check on Windows. Does not replace Xcode type checking."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'.tools/ipad-parser'))
import tree_sitter,tree_sitter_swift

parser=tree_sitter.Parser(tree_sitter.Language(tree_sitter_swift.language()))
errors=[]
files=list((ROOT/'ipad').rglob('*.swift'))
for file in files:
    data=file.read_bytes(); tree=parser.parse(data)
    def visit(node):
        if node.type=='ERROR' or node.is_missing:
            errors.append(f'{file.relative_to(ROOT)}:{node.start_point.row+1}:{node.start_point.column+1} {node.type}: {data[node.start_byte:min(node.end_byte,node.start_byte+100)]!r}')
        for child in node.children: visit(child)
    visit(tree.root_node)
if errors:
    print('\n'.join(errors)); sys.exit(1)
print(f'Swift grammar parsed: {len(files)} files. Type checking and SDK availability require Xcode.')

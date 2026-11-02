package ru.folio.notes;
import android.graphics.Canvas;import java.util.*;
class NotesView {
    final MainActivity a;Ink.Page page;int penId=-1;Object draft,gestureBefore;boolean moving;HashSet<Ink.Stroke> selection=new HashSet<>();int checkpoints;
    NotesView(MainActivity a){this.a=a;page=a.current.page();}
    int getWidth(){return 1000;}int getHeight(){return 1000;}float px(float x){return x;}float py(float y){return y;}void draw(Canvas c){}
    void checkpoint(){checkpoints++;}void indexAll(){}void rebuild(){}void changed(){a.revision++;a.current.modified++;}
}

import SwiftUI

struct WelcomeView:View {
    let done:()->Void
    @State private var page=0
    let slides:[(String,String,String)] = [
        ("Мысли без границ","Расширяйте холст двумя пальцами. Места хватит для любой идеи.","arrow.up.left.and.arrow.down.right"),
        ("Почувствуйте штрих","Нажим и наклон меняют живую кисть. Pencil Pro добавляет вращение.","paintbrush.pointed.fill"),
        ("Инструменты рядом","Сожмите Pencil Pro, чтобы открыть круговой пенал возле наконечника.","circle.hexagongrid"),
        ("Два касания — и назад","Коснитесь экрана двумя пальцами, чтобы отменить последнее действие.","arrow.uturn.backward"),
        ("Всё останется с вами","Folio сохраняет тетрадь автоматически. Экспортируйте копию в Файлы.","checkmark.seal")]
    var body:some View {
        VStack(spacing:0) {
            HStack { Text("Folio").font(.system(size:24,weight:.semibold,design:.rounded)); Spacer(); Button("Пропустить",action:done).accessibilityIdentifier("skipWelcome") }.padding(30)
            TabView(selection:$page) {
                ForEach(slides.indices,id:\.self) { i in
                    VStack(spacing:28) {
                        FeatureAnimation(feature:i).frame(maxWidth:520).frame(height:300).padding(.horizontal,40)
                        Text(slides[i].0).font(.system(size:38,weight:.semibold,design:.rounded)).multilineTextAlignment(.center)
                        Text(slides[i].1).font(.system(size:20)).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth:510).padding(.horizontal,30)
                    }.tag(i).padding(.bottom,50)
                }
            }.tabViewStyle(.page(indexDisplayMode:.always))
            Button { if page == slides.count-1 { done() } else { withAnimation(.spring(response:0.5,dampingFraction:0.85)) { page+=1 } } } label: {
                HStack { Text(page == slides.count-1 ? "Открыть Folio":"Продолжить"); Image(systemName:"arrow.right") }.font(.headline).frame(width:270,height:58)
            }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).padding(.bottom,48)
        }.background(Color(uiColor:FolioTheme.paper)).tint(Color(uiColor:FolioTheme.accent))
    }
}

struct FeatureAnimation:View {
    let feature:Int
    @Environment(\.accessibilityReduceMotion) var reduceMotion
    @State private var animate=false
    var body:some View {
        ZStack {
            RoundedRectangle(cornerRadius:36).fill(.white).shadow(color:.black.opacity(0.05),radius:30,y:14)
            if feature == 0 {
                Canvas { context,size in
                    for x in stride(from:0.0,to:size.width,by:28) { for y in stride(from:0.0,to:size.height,by:28) { context.fill(Path(ellipseIn:CGRect(x:x,y:y,width:2,height:2)),with:.color(.gray.opacity(0.2))) } }
                }.clipShape(RoundedRectangle(cornerRadius:36))
                Image(systemName:"arrow.up.left.and.arrow.down.right").font(.system(size:76,weight:.ultraLight)).scaleEffect(animate ? 1.2:0.85)
            } else if feature == 1 {
                WaveShape().trim(from:0,to:animate ? 1:0.02).stroke(Color(uiColor:FolioTheme.accent),style:StrokeStyle(lineWidth:12,lineCap:.round)).padding(60)
                Image(systemName:"pencil.tip").font(.system(size:42)).rotationEffect(.degrees(animate ? 20:-20)).offset(x:animate ? 130:-130,y:20)
            } else if feature == 2 {
                ForEach(0..<6) { i in
                    let a=Double(i)*Double.pi/3
                    Image(systemName:FolioTool.allCases[i].symbol).font(.system(size:27)).frame(width:54,height:54).background(Color(uiColor:FolioTheme.paper),in:Circle())
                        .offset(x:cos(a)*(animate ? 90:0),y:sin(a)*(animate ? 90:0)).opacity(animate ? 1:0)
                }
                Image(systemName:"pencil.tip").font(.system(size:34)).rotationEffect(.degrees(-30))
            } else if feature == 3 {
                WaveShape().trim(from:0,to:animate ? 0:1).stroke(Color(uiColor:FolioTheme.accent),style:StrokeStyle(lineWidth:5,lineCap:.round)).padding(65)
                HStack(spacing:18) { Circle().frame(width:34); Circle().frame(width:34) }.foregroundStyle(Color(uiColor:FolioTheme.accent).opacity(0.18)).scaleEffect(animate ? 0.9:1.1)
            } else {
                Image(systemName:"checkmark.seal").font(.system(size:94,weight:.ultraLight)).scaleEffect(animate ? 1:0.7).opacity(animate ? 1:0.4)
                ForEach(0..<3) { i in Circle().stroke(Color(uiColor:FolioTheme.accent).opacity(0.1),lineWidth:1).frame(width:CGFloat(145+i*36),height:CGFloat(145+i*36)).scaleEffect(animate ? 1.1:0.9) }
            }
        }.foregroundStyle(Color(uiColor:FolioTheme.accent))
        .onAppear { if reduceMotion { animate=true } else { withAnimation(.easeInOut(duration:2).repeatForever(autoreverses:true)) { animate=true } } }
        .onDisappear { animate=false }
    }
}

struct WaveShape:Shape {
    func path(in rect:CGRect)->Path {
        var path=Path(); path.move(to:CGPoint(x:0,y:rect.midY))
        path.addCurve(to:CGPoint(x:rect.maxX,y:rect.midY),control1:CGPoint(x:rect.width*0.3,y:-rect.height*0.3),control2:CGPoint(x:rect.width*0.65,y:rect.height*1.3))
        return path
    }
}

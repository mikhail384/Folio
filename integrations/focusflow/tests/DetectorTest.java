import ru.focusflow.pen.DoubleSqueezeDetector;

public class DetectorTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        DoubleSqueezeDetector d=new DoubleSqueezeDetector();
        check(!d.accept(true,1000,550),"First down");check(!d.accept(false,1123,550),"Single release");
        check(!d.accept(true,1176,550),"Second down");check(d.accept(false,1296,550),"Actual tablet double");
        check(!d.accept(true,1350,550),"Cooldown down");check(!d.accept(false,1430,550),"Triple release must not fire");
        d.reset();d.accept(true,2000,550);d.accept(false,2120,550);d.accept(true,2800,550);check(!d.accept(false,2920,550),"Too slow");
        d.reset();d.accept(true,3000,550);d.accept(true,3001,550);check(!d.accept(false,3150,550),"Repeat down is not double");
        d.reset();d.accept(true,4000,550);d.accept(false,5100,550);d.accept(true,5200,550);check(!d.accept(false,5300,550),"Long hold is not squeeze");
        d.reset();check(!d.accept(false,6000,550),"Orphan release");
        d.accept(true,6100,550);d.accept(false,6200,550);d.accept(true,6750,550);check(d.accept(false,6800,550),"Boundary window");
        d.reset();d.accept(true,7000,550);d.accept(false,7100,550);d.reset();d.accept(true,7200,550);check(!d.accept(false,7300,550),"Config reset clears pending squeeze");
        System.out.println("PASS: "+checks+" detector assertions");
    }
}

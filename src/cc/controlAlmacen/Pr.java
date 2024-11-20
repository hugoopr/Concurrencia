package cc.controlAlmacen;

public class Pr extends Thread{
	pruebas p;
	public Pr(pruebas p) {
		this.p = p;
	}
	
	public void run() {
		while(true) {
			p.op1();
			p.op2();
			p.op1();
			p.op2();
		}
	}
}

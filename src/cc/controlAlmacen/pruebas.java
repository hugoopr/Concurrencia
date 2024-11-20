package cc.controlAlmacen;

import es.upm.babel.cclib.Monitor;

public class pruebas {

	private Monitor m = new Monitor();
	private Monitor.Cond c1 = m.newCond();
	private Monitor.Cond c2 = m.newCond();
	int self = 0;
	
	public pruebas() {};
	public void op1 () {
		m.enter();
		if(self > 0) {
			c1.await();
		}
		self ++;
		desbloqueo();
		System.out.println("P1:"+ self);

		m.leave();
	}
	public void op2() {
		m.enter();
		if(self < 0) {
			c2.await();
		}
		self = -1;
		c1.signal();
		System.out.println("P2");

		m.leave();
	}
	
	public void desbloqueo() {
		if(self<=0) c1.signal();
		if(self>=0) c2.signal();
	}
	
	public static void main(String[]args) {
		pruebas pp = new pruebas();

		pp.op1();
		
	}
	
	
}

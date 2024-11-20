package cc.controlAlmacen;

import java.util.Iterator;

import es.upm.aedlib.Position;
import es.upm.aedlib.positionlist.*;
import es.upm.babel.cclib.Monitor;
import java.util.Map;
import java.util.HashMap;


public class ControlAlmacenMonitor implements ControlAlmacen {

  // Resource state
  // ...
	private Map<String,Integer[]> productos = null; // lista de productos con sus 4 valores asociados en un array

  // Monitors and conditions
  // ...
	private Monitor mon = new Monitor();	// monitor
	class Detectar{
		  public String itemId;
		  public Monitor.Cond condEntregar;
		  public int entregar;
		  
		  public Detectar(String itemId, int entregar) {
			  this.itemId = itemId;
			  this.condEntregar = mon.newCond();
			  this.entregar = entregar;
		  }
	}
	class Detectar2{
		  public String itemId;
		  public Monitor.Cond condOfReab;
		  
		  public Detectar2(String itemId) {
			  this.itemId = itemId;
			  this.condOfReab = mon.newCond();
		  }
	}
	PositionList<Detectar> espDetectar = new NodePositionList<Detectar>();
	PositionList<Detectar2> espDetectar2 = new NodePositionList<Detectar2>();

  public ControlAlmacenMonitor(Map<String,Integer> tipoProductos) {
	  productos = new HashMap<String,Integer[]>();
	  
	  Iterator<String> it = tipoProductos.keySet().iterator();
	  while(it.hasNext()) {	// recorremos el mapa de productos dado como parametro
		  Integer[] valores = new Integer[4];
		  valores[0]=0;	
		  valores[1]=0;
		  valores[2]=0;	// se inician a 0 los disponibles, enCamino y comprados
		  String idP = it.next();
		  valores[3] = tipoProductos.get(idP);	// añadimos el valor de min_disponible^
		  productos.put(idP, valores);	// añadimos el producto con sus valores a la lista de productos
	  }
  }

  public boolean comprar(String clientId, String itemId, int cantidad) {
	mon.enter();
	boolean res = false;
	// PRE: cantidad>0 && itemId pertenece a dom self   
	Integer[] valores = productos.get(itemId);
	Integer[] valoresPre= new Integer[4]; // hacemos una copia para comparar los valores despues de la seccion critica y saber si return true o false
	if(cantidad<=0 || !productos.containsKey(itemId)) {
		mon.leave();
		throw new IllegalArgumentException(); // lanzamos la excepcion
	}

	for(int i=0;i<4;i++)valoresPre[i] = valores[i]; // copiamos los valores iniciales antes de modificarlos en la seccion critica
	// monitor
	
	//SC:
	res = valoresPre[0]+valoresPre[1]>=cantidad+valoresPre[2];
	if(res) {
		valores[2] += cantidad;
		productos.put(itemId, valores);
	}
	
	desbloqueo2(itemId);
	//condiciones del result
	res = valoresPre[2]+cantidad == valores[2] && valores!=valoresPre;
	mon.leave();
	
    return res;
  }

  public void entregar(String clientId, String itemId, int cantidad) {
	  mon.enter();
	  
	// PRE: cantidad>0 && itemId pertenece a dom self
	  Integer[] valores = productos.get(itemId);
	  if(cantidad<=0 || !productos.containsKey(itemId)) {
		  mon.leave();
		  throw new IllegalArgumentException();
	  }
	  boolean eA = buscar(itemId); // miramos si hay alguna entrega atrasada del mismo producto
	  // CPRE: itemId.Disponible>=cantidad
	  if(valores[0]<cantidad || eA) {
		  Detectar d = new Detectar(itemId, cantidad);
		  espDetectar.addLast(d);
		  d.condEntregar.await();
	  }
	  
	  //SC:
	  valores[0] -= cantidad;
	  valores[2] -= cantidad;
	  productos.put(itemId, valores);
	  
	  desbloqueo(itemId);	//liberamos el monitor condicional de OfreceReabastecer
	  mon.leave();

  }

  public void devolver(String clientId, String itemId, int cantidad) {
	  mon.enter();
	// PRE: cantidad>0 && itemId pertenece a dom self   
	  Integer[] valores = productos.get(itemId);
	  if(cantidad<=0 || !productos.containsKey(itemId)) {
		  mon.leave();
		  throw new IllegalArgumentException();
	  }

	  //SC:
	  valores[0] += cantidad;
	  productos.put(itemId, valores);
	  
	  desbloqueo(itemId);
	  mon.leave();
  }

  public void ofrecerReabastecer(String itemId, int cantidad) {
	  mon.enter();
	  
	// PRE: cantidad>0 && itemId pertenece a productos   
	  Integer[] valores = productos.get(itemId);
	  if(cantidad<=0 || !productos.containsKey(itemId)) {
		  mon.leave();
		  throw new IllegalArgumentException();
	  }

	// CPRE: self.disponibles + self.enCamino - self.comprados < self.minDisponibles
	  if((valores[0]+valores[1]-valores[2])>=valores[3]) {
			  Detectar2 d = new Detectar2(itemId);
			  espDetectar2.addLast(d);
			  d.condOfReab.await();
	  }
		
	  //SC:
	  valores[1] += cantidad;
	  productos.put(itemId, valores);
	  
	  mon.leave();
  }

  public void reabastecer(String itemId, int cantidad) {
	  	mon.enter();
	// PRE: cantidad>0 && itemId pertenece a dom self   
		Integer[] valores = productos.get(itemId);
		if(cantidad<=0 || !productos.containsKey(itemId)) {
			mon.leave();
			throw new IllegalArgumentException();
		}
		
		//SC:
		valores[0] += cantidad;
		valores[1] -= cantidad;
		productos.put(itemId, valores);
		
		desbloqueo(itemId);	//liberamos el mon condicional de Entregar
		mon.leave();
  }
  
  private void desbloqueo(String idItem) {
	  boolean des = false;
	  Position<Detectar> p = null;
	  if(idItem!=null && !espDetectar.isEmpty()) {
		p = espDetectar.first();
		Integer[]val = productos.get(idItem);
		Iterator<Detectar> it = espDetectar.iterator();
	  	while(it.hasNext() && !des) {
	  		Detectar d = it.next();
	  		if(d.itemId.equals(idItem)) des=true;
	  		if(val[0]>=d.entregar && des && d.condEntregar.waiting() > 0) {
	  			espDetectar.remove(p);
	  			d.condEntregar.signal();
	  		}
	  		if(!des)p = espDetectar.next(p);
	  	}
	  		
	  	
	  }
  }
  
  private void desbloqueo2(String itemId) {
	  boolean des = false;
	  Position<Detectar2> p = null;
	  if(itemId!=null && !espDetectar2.isEmpty()) {
		p = espDetectar2.first();
		Integer[]val = productos.get(itemId);
		Iterator<Detectar2> it = espDetectar2.iterator();
	  	while(it.hasNext() && !des) {
	  		Detectar2 d = it.next();
	  		if(d.itemId.equals(itemId)) des=true;
	  		if(val[0]+val[1]-val[2]<val[3] && des && d.condOfReab.waiting() > 0) {
	  			espDetectar2.remove(p);
	  			d.condOfReab.signal();
	  		}
	  		if(!des)p = espDetectar2.next(p);
	  	}
	  }	  
  }
  
  private boolean buscar(String i) {
	  boolean res = false;
	  Iterator<Detectar> it = espDetectar.iterator();
	  while(it.hasNext() && !res) {
		  Detectar d = it.next();
		  res = d.itemId.equals(i);
	  }
	  return res;
  }
  
}

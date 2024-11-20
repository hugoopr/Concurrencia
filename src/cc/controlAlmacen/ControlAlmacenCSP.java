package cc.controlAlmacen;

import java.util.Iterator;

//paso de mensajes con JCSP
import org.jcsp.lang.*;
import java.util.Map;

import es.upm.aedlib.Position;
import es.upm.aedlib.positionlist.*;

//
//
//TODO: otros imports
//
//


public class ControlAlmacenCSP implements ControlAlmacen, CSProcess {

 // algunas constantes de utilidad
 static final int PRE_KO  = -1;
 static final int NOSTOCK =  0;
 static final int STOCKOK =  1;
 static final int SUCCESS =  0;
 // TODO: añadid las que creáis convenientes
 
 // canales para comunicación con el servidor
 private final Any2OneChannel chComprar            = Channel.any2one();
 private final Any2OneChannel chEntregar           = Channel.any2one();
 private final Any2OneChannel chDevolver           = Channel.any2one();
 private final Any2OneChannel chOfrecerReabastecer = Channel.any2one();
 private final Any2OneChannel chReabastecer        = Channel.any2one();

 // Resource state --> server side !!

 // peticiones de comprar
 private static class PetComprar {
	public String productId;
	public int    q;
	public One2OneChannel chresp;

	PetComprar (String productId, int q) {
	    this.productId = productId;
	    this.q         = q;
	    this.chresp = Channel.one2one();
	}
 }

 // peticiones de entregar
 private static class PetEntregar {
	 public String productId;
	 public int    q;
	 public One2OneChannel chresp;

	 PetEntregar (String productId, int q) {
		 this.productId = productId;
		 this.q         = q;
		 this.chresp = Channel.one2one();
	} 
 }

 // peticiones de devolver
 private static class PetDevolver {
	 public String productId;
	 public int    q;
	 public One2OneChannel chresp;

	 PetDevolver (String productId, int q) {
		this.productId = productId;
		this.q         = q;
		this.chresp = Channel.one2one();
	}
 }

 // peticiones de ofrecerReabastecer
 private static class PetOfrecerReabastecer {
	 public String productId;
	 public int    q;
	 public One2OneChannel chresp;

	 PetOfrecerReabastecer (String productId, int q) {
		this.productId = productId;
		this.q         = q;
		this.chresp = Channel.one2one();
	}
 }

 // peticiones de reabastecer
 private static class PetReabastecer {
	 public String productId;
	 public int    q;
	 public One2OneChannel chresp;

	 PetReabastecer (String productId, int q) {
		this.productId = productId;
		this.q         = q;
		this.chresp = Channel.one2one();
	}
 }
 
 // INTERFAZ ALMACEN
 public boolean comprar(String clientId, String itemId, int cantidad) {

	// petición al servidor
	PetComprar pet = new PetComprar(itemId,cantidad);
	chComprar.out().write(pet);
	
   	// recibimos contestación del servidor
	// puede ser una de {PRE_KO, NOSTOCK, STOCKOK}
	int respuesta = (Integer)pet.chresp.in().read();

	// no se cumple PRE:
	if (respuesta == PRE_KO)
	    throw new IllegalArgumentException();
	// se cumple PRE:
	return (respuesta == STOCKOK);
 }

 public void entregar(String clientId, String itemId, int cantidad) {
	// petición al servidor
	PetEntregar pet = new PetEntregar(itemId,cantidad);
	chEntregar.out().write(pet);
		
	// recibimos contestación del servidor
	// puede ser una de {PRE_KO, NOSTOCK, STOCKOK}
	int respuesta = (Integer)pet.chresp.in().read();

	// no se cumple PRE:
	if (respuesta == PRE_KO) throw new IllegalArgumentException();
 }

 public void devolver(String clientId, String itemId, int cantidad) {
	// petición al servidor
	PetDevolver pet = new PetDevolver(itemId,cantidad);
	chDevolver.out().write(pet);
			
	// recibimos contestación del servidor
	// puede ser una de {PRE_KO, NOSTOCK, STOCKOK}
	int respuesta = (Integer)pet.chresp.in().read();

	// no se cumple PRE:
	if (respuesta == PRE_KO) throw new IllegalArgumentException(); 
 }

 public void ofrecerReabastecer(String itemId, int cantidad) {
	 PetOfrecerReabastecer pet = new PetOfrecerReabastecer(itemId,cantidad);
	 chOfrecerReabastecer.out().write(pet);
			
	 // recibimos contestación del servidor
	 // puede ser una de {PRE_KO, NOSTOCK, STOCKOK}
	 int respuesta = (Integer)pet.chresp.in().read();

	 // no se cumple PRE:
	 if (respuesta == PRE_KO) throw new IllegalArgumentException(); 
 }
 
 public void reabastecer(String itemId, int cantidad) {
	// petición al servidor
	PetReabastecer pet = new PetReabastecer(itemId,cantidad);
	chReabastecer.out().write(pet);
			
	// recibimos contestación del servidor
	// puede ser una de {PRE_KO, NOSTOCK, STOCKOK}
	int respuesta = (Integer)pet.chresp.in().read();

	// no se cumple PRE:
	if (respuesta == PRE_KO) throw new IllegalArgumentException();
 }
	
 // atributos de la clase
 Map<String,Integer> tipoProductos; // stock mínimo para cada producto
 PositionList<MyItem> productos = new NodePositionList<MyItem>(); // lista de productos

 public ControlAlmacenCSP(Map<String,Integer> tipoProductos) {
	this.tipoProductos = tipoProductos;
	tipoProductos.forEach((k,v) -> productos.addLast(new MyItem(k,0,0,0,v))); // añadimos cada producto a la lista con los valores iniciales correspondientes
	
	new ProcessManager(this).start(); // al crearse el servidor también se arranca...
 }

 boolean ejecutar_Comprar(PetComprar p) {
	 boolean res=false;
	 String s = p.productId;
	 MyItem i = recorrer(s); // obtenemos el producto de la peticion
	 if(i.disponibles + i.enCamino >= p.q + i.comprados) { // comprobamos la condicion del POST
		 i.setComprados(i.comprados + p.q); //SC
		 res=true;
	 }
	 return res; // devolvemos si se ha podido comprar o no
 }
 boolean ejecutar_Entregar(PetEntregar p) {
		boolean res = false;
		String s = p.productId;
		MyItem i = recorrer(s);
		if(i.disponibles>=p.q) { // CPRE: disponibles>=cantidad 
			res = true; // si se cumple CPRE
			i.setDisponibles(i.disponibles-p.q); // SC
			i.setComprados(i.comprados-p.q);
		}
		return res;
 }
 
 boolean ejecutar_Devolver(PetDevolver p) {
	 String s = p.productId;
	 MyItem i = recorrer(s);
	 i.setDisponibles(p.q+i.disponibles); // SC
	 return true; // no hay CPRE -> siempre se hace la peticion
 }
 
 boolean ejecutar_OfReabastecer(PetOfrecerReabastecer p) {
	 boolean res = false;
	 String s = p.productId;
	 MyItem i = recorrer(s);
	 if(i.disponibles + i.enCamino - i.comprados < i.min) { // CPRE: disponibles + enCamino -comprados < minDisponibles
		 res = true; // CPRE se cumple
		 i.setEnCamino(i.enCamino + p.q); // SC
	 }
	 return res; // se cumple CPRE o no?
 }
 
 boolean ejecutar_Reabastecer(PetReabastecer p) {
	 String s = p.productId;
	 MyItem i = recorrer(s);
	 i.setDisponibles(i.disponibles+p.q); // SC
	 i.setEnCamino(i.enCamino-p.q);
	 return true; // no hay CPRE
 }
 
 
 //metodo para recorrer la lista de productos y obtener el Producto de la peticion (mismo ID)
 MyItem recorrer(String s) {
	 MyItem i = null;
	 boolean fin = false;
	 Iterator<MyItem> it = productos.iterator();
	 while(it.hasNext() && !fin) {
		 i = it.next();
		 fin = i.itemId.equals(s);
	 }
	 return fin?i:null; // si fin = false, no está en la list -> null
 }
 
 // peticiones aplazadas de entregar
 class PeticionAplEntregar {
		public One2OneChannel resp; // canal de respuesta
		PetEntregar p; // peticion aplazada
			
		public PeticionAplEntregar(One2OneChannel c, PetEntregar p) {
			resp=c;
			this.p = p;
		}
	 }

 // peticiones aplazada de ofrecer reabastecer
 class PeticionAplOfReabastecer {
		public One2OneChannel resp;
		PetOfrecerReabastecer p;
		
		public PeticionAplOfReabastecer(One2OneChannel c, PetOfrecerReabastecer p) {
			resp=c;
			this.p = p;
		}
 }
 
 
//SERVIDOR
 public void run() {

	// para recepción alternativa condicional
	Guard[] entradas = {
	    chComprar.in(),
	    chEntregar.in(),
	    chDevolver.in(),
	    chOfrecerReabastecer.in(),
	    chReabastecer.in()
	};
	Alternative servicios =  new Alternative (entradas);
	// OJO ORDEN!!
	final int COMPRAR             = 0;
	final int ENTREGAR            = 1;
	final int DEVOLVER            = 2;
	final int OFRECER_REABASTECER = 3;
	final int REABASTECER         = 4;
	// condiciones de recepción: todas a CIERTO

	// estado del recurso -> iniciado en el constructor
	// TODO: vuestra estructura de datos traída de monitores
	
	// TODO: estructuras aux. para peticiones aplazadas
	// entregar
	PositionList<PeticionAplEntregar> p1; // lista de peticiones aplazadas de entregar
	
	// ofrecerReabastecer
	PositionList<PeticionAplOfReabastecer> p2; // lista de peticiones aplazadas de of reabastecer

	
	
	// inicializaciones
	p1 = new NodePositionList<PeticionAplEntregar>();
	p2 = new NodePositionList<PeticionAplOfReabastecer>();
 
	// bucle de servicio
	while (true) {
	    // vars. auxiliares
	    // tipo de la última petición atendida
	    int choice = -1; // una de {COMPRAR, ENTREGAR, DEVOLVER, OFRECER_REABASTECER, REABASTECER}
	    
	    // todas las peticiones incluyen un producto y una cantidad
	    boolean res;
	    String s=null; // string para ver que producto es de las peticiones que probamos a liberar
	    
	    choice = servicios.fairSelect();
	    switch (choice) {
	    case COMPRAR: // CPRE = Cierto
		PetComprar petC = (PetComprar)chComprar.in().read();
		// comprobar PRE:
		// ** CÓDIGO ORIENTATIVO!! ADAPTAD A VUESTRA ESTRUCTURA DE DATOS!! **
		if (petC.q < 1 || recorrer(petC.productId) == null) { // PRE_KO
		    petC.chresp.out().write(PRE_KO);
		} else { // PRE_OK 
			s = petC.productId;
		    res = ejecutar_Comprar(petC);
		    if(res) petC.chresp.out().write(STOCKOK);
		    else petC.chresp.out().write(NOSTOCK);
		}    
		break;
	    case ENTREGAR: // CPRE en diferido
		PetEntregar petE = (PetEntregar)chEntregar.in().read();
		if (petE.q < 1 || recorrer(petE.productId) == null) { // PRE_KO
		    petE.chresp.out().write(PRE_KO);
		} else { // PRE_OK
			s = petE.productId;
			res = ejecutar_Entregar(petE);
			if(res) { // si CPRE es cierto, o no tiene -> se ha ejecutado la SC
				petE.chresp.out().write(SUCCESS);
			}else {	// si la CPRE no se ha cumplido
				PeticionAplEntregar p = new PeticionAplEntregar(petE.chresp, petE);
				p1.addLast(p); // añadimos a la lista de peticiones aplazadas
			}
		}
		
		break;
	    case DEVOLVER: // CPRE = Cierto
		PetDevolver petD = (PetDevolver)chDevolver.in().read();
		if (petD.q < 1 || recorrer(petD.productId) == null) { // PRE_KO
		    petD.chresp.out().write(PRE_KO);
		} else { // PRE_OK
			s = petD.productId;
			res = ejecutar_Devolver(petD);
			if(res) {
				petD.chresp.out().write(SUCCESS); // res true -> se ha ejecutado la SC
			}
		}
		
		break;
	    case OFRECER_REABASTECER: // CPRE en diferido
		PetOfrecerReabastecer petO = (PetOfrecerReabastecer)chOfrecerReabastecer.in().read();
		if (petO.q < 1 || recorrer(petO.productId)==null) { // PRE_KO
		    petO.chresp.out().write(PRE_KO);
		} else { // PRE_OK
			s = petO.productId;
			res = ejecutar_OfReabastecer(petO);
			if(res) { // si CPRE es cierto, o no tiene -> se ha ejecutado la SC
				petO.chresp.out().write(SUCCESS);
			}else {	// si la CPRE no se ha cumplido
				PeticionAplOfReabastecer p = new PeticionAplOfReabastecer(petO.chresp, petO);
				p2.addLast(p); // añadimos a la lista de peticiones aplazadas
			}
		}	
		break;
	    case REABASTECER: // CPRE = Cierto
		PetReabastecer petR = (PetReabastecer)chReabastecer.in().read();
		if (petR.q < 1 || recorrer(petR.productId) == null) { // PRE_KO
		    petR.chresp.out().write(PRE_KO);
		} else { // PRE_OK
			s = petR.productId;
			res = ejecutar_Reabastecer(petR);
			if(res) {
				petR.chresp.out().write(SUCCESS);
			}
		}
		break;
	    } // switch
	
	    // tratamiento de peticiones aplazadas
	    	    
	    // peticiones de entregar
	   
	    if(!p1.isEmpty())revisarEntregar(p1,s); // si hay peticiones aplazadas tratamos de desbloquearlas

	    // peticiones de ofrecer reabastecer
	    
	    if(!p2.isEmpty())revisarOfReab(p2,s);
	    
	    // asegurar que no quedan peticiones aplazadas
	    // que podrian ser atendidas!!!!!!!!!!!!!!!!!!
	    	    
	} // bucle servicio
	
 } // run SERVER
 private void revisarEntregar (PositionList<PeticionAplEntregar> p1,String s) {
	 	Position<PeticionAplEntregar> pos = null;
		Position<PeticionAplEntregar> pos2 = null; // positions para recorrer la lista
		boolean r = true;
		boolean avanza = true;
		if(p1!=null)pos=p1.first();
		// si pos==null hemos recorrido toda lista y si r=false significa que la peticion no puede ser liberada todavia y al tener prioridad
		// la que mas tiempo lleva no podemos liberar el resto por lo que no seguimos recorriendo
		while(pos!=null && r) {
			avanza = true;
			PeticionAplEntregar p = pos.element();
			if(p.p.productId.equals(s)) {
			   avanza = false; // si la peticion es del item deseado no habrá que avanzar luego porque:
			   // si r true ya avanzamos dentro del if, si r false vamos a salirnos del metodo
			   r = ejecutar_Entregar(p.p); // comprobamos si cumple el CPRE o no
			   if(r) { // si cumple lo eliminamos de la lista de peticiones y enviamos el mensaje de SUCCESS
				   p.resp.out().write(SUCCESS);
				   pos2=pos;
				   pos = p1.next(pos);
				   p1.remove(pos2);
			   } 
			}
			if(avanza)pos=p1.next(pos); // si la peticion no es del objeto deseado -> avanzamos a la siguiente
		}
}
 
 // igual que la de Entregar
 private void revisarOfReab (PositionList<PeticionAplOfReabastecer> p2, String s) {
	   Position<PeticionAplOfReabastecer> pos = null;
	   Position<PeticionAplOfReabastecer> pos2 = null;
	   boolean avanza=true;
	   boolean r = true;
	   if(p2!=null)pos=p2.first();
	   while(pos!=null && r) {
		   avanza = true;
		   PeticionAplOfReabastecer p = pos.element();
		   if(p.p.productId.equals(s)) {
			   avanza = false;
			   r = ejecutar_OfReabastecer(p.p);
			   if(r) {
				   p.resp.out().write(SUCCESS);
				   pos2=pos;
				   pos = p2.next(pos);
				   p2.remove(pos2);
			   }
		   }
		   if(avanza)pos = p2.next(pos);
	   }
 }
 
}
 // Clases auxiliares
 // PRODUCTOS CON SUS VALORES
 	class MyItem{
 		String itemId;
 		int disponibles;
 		int enCamino;
 		int comprados;
 		int min;
 		
 		public MyItem(String itemId, int disponibles, int enCamino, int comprados, int min) {
 			this.itemId = itemId;
 			this.disponibles = disponibles;
 			this.enCamino = enCamino;
 			this.comprados = comprados;
 			this.min = min;
 		}

 		// actualizacion de valores en la SC
 		void setDisponibles(int n) {
 			this.disponibles = n;
 		}
 		void setEnCamino(int n) {
 			this.enCamino = n;
 		}
 		void setComprados(int n) {
 			this.comprados = n;
 		}
 	}
 	
 	
 
 //
 

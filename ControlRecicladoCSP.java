// Esqueleto para realizar la practica mediante paso de mensajes y
// peticiones aplazadas (rellenad el código donde dice COMPLETAD y
// eliminad todos los comentarios espúreos, como este, antes de
// realizar la entrega)
package cc.controlReciclado;

import org.jcsp.lang.*;

// importar estructuras de datos para almacenar peticiones aplazadas
import java.util.LinkedList;
import java.util.Queue;

public class ControlRecicladoCSP implements ControlReciclado, CSProcess {

	// constantes varias
	private enum Estado {
		LISTO, SUSTITUIBLE, SUSTITUYENDO
	}

	private final int MAX_P_CONTENEDOR; // a definir en el constructor
	private final int MAX_P_GRUA; // a definir en el constructor

	// canales para comunicación con el servidor y RPC
	// uno por operacion (peticiones aplazadas)
	private final Any2OneChannel chNotificarPeso;
	private final Any2OneChannel chIncrementarPeso;
	private final Any2OneChannel chNotificarSoltar;
	private final Any2OneChannel chPrepararSustitucion;
	private final Any2OneChannel chNotificarSustitucion;

	// para aplazar peticiones de incrementarPeso
	// esta va de regalo
	private static class PetIncrementarPeso {
		public int p;
		public One2OneChannel chACK;

		PetIncrementarPeso(int p) {
			this.p = p;
			this.chACK = Channel.one2one();
		}
	}

	public ControlRecicladoCSP(int max_p_contenedor, int max_p_grua) {
		// constantes del sistema
		MAX_P_CONTENEDOR = max_p_contenedor;
		MAX_P_GRUA = max_p_grua;

		// creación de los canales
		chNotificarPeso = Channel.any2one();
		chIncrementarPeso = Channel.any2one();
		chNotificarSoltar = Channel.any2one();
		chPrepararSustitucion = Channel.any2one();
		chNotificarSustitucion = Channel.any2one();

		// arranque del servidor desde el constructor OJO!!!
		new ProcessManager(this).start();
	}

	// interfaz ControlReciclado

	// PRE: 0 < p < MAX_P_GRUA
	// CPRE: self.estado =/= SUSTITUYENDO
	// notificarPeso(p)
	public void notificarPeso(int p) throws IllegalArgumentException {
		// tratar PRE
		if (p <= 0 || p > MAX_P_GRUA)
			throw new IllegalArgumentException(new Exception("La grua ha colapsado"));

		// PRE OK, enviar petición
		chNotificarPeso.out().write(p);
	}

	// PRE: 0 < p < MAX_P_GRUA
	// CPRE: self.estado =/= SUSTITUYENDO /\
	// self.peso + p <= MAX_P_CONTENEDOR
	// incrementarPeso(p)
	public void incrementarPeso(int p) throws IllegalArgumentException {
		// tratar PRE
		if (p <= 0 || p > MAX_P_GRUA)
			throw new IllegalArgumentException(new Exception("La grua ha colapsado"));

		// PRE OK, creamos peticion para el servidor
		PetIncrementarPeso peticion = new PetIncrementarPeso(p);

		// enviamos peticion
		chIncrementarPeso.out().write(peticion);

		// esperar confirmacion
		peticion.chACK.in().read();
	}

	// PRE: --
	// CPRE: --
	// notificarSoltar()
	public void notificarSoltar() {
		// enviar peticion
		chNotificarSoltar.out().write(null);
	}

	// PRE: --
	// CPRE: self = (_, sustituible, 0)
	// prepararSustitucion()
	public void prepararSustitucion() {
		// enviar peticion
		chPrepararSustitucion.out().write(null);
	}

	// PRE: --
	// CPRE: --
	// notificarSustitucion()
	public void notificarSustitucion() {
		// enviar peticion
		chNotificarSustitucion.out().write(null);
	}

	// SERVIDOR
	public void run() {
		// estado del recurso
		int pesoContenedor = 0;
		Estado estado = Estado.LISTO;
		int accediendo = 0;

		// para recepción alternativa condicional
		Guard[] entradas = { chNotificarPeso.in(), chIncrementarPeso.in(), chNotificarSoltar.in(),
				chPrepararSustitucion.in(), chNotificarSustitucion.in() };
		Alternative servicios = new Alternative(entradas);
		// OJO ORDEN!!
		final int NOTIFICAR_PESO = 0;
		final int INCREMENTAR_PESO = 1;
		final int NOTIFICAR_SOLTAR = 2;
		final int PREPARAR_SUSTITUCION = 3;
		final int NOTIFICAR_SUSTITUCION = 4;
		// condiciones de recepción
		final boolean[] sincCond = new boolean[5];

		sincCond[NOTIFICAR_SOLTAR] = true;
		sincCond[NOTIFICAR_SUSTITUCION] = true;

		// creamos colección para almacenar peticiones aplazadas
		Queue<PetIncrementarPeso> aplazadas = new LinkedList<PetIncrementarPeso>();

		// bucle de servicio
		while (true) {
			// vars. auxiliares para comunicación con clientes
			int pesoGrua;
			PetIncrementarPeso peticion;

			// actualización de condiciones de recepción cerrando los canales los cuales
			// no cumplan las precondiciones necesarias para ejecutar su funcionalidad
			sincCond[NOTIFICAR_PESO] = estado != Estado.SUSTITUYENDO;
			sincCond[INCREMENTAR_PESO] = estado != Estado.SUSTITUYENDO;
			sincCond[PREPARAR_SUSTITUCION] = estado == Estado.SUSTITUIBLE && accediendo == 0;

			switch (servicios.fairSelect(sincCond)) {
			case NOTIFICAR_PESO:
				// estado != Estado.SUSTITUYENDO
				// leer petición
				pesoGrua = (int) chNotificarPeso.in().read();

				// procesar petición
				if (pesoGrua + pesoContenedor > MAX_P_CONTENEDOR) {
					estado = Estado.SUSTITUIBLE;
				} else {
					estado = Estado.LISTO;
				}

				break;
			case INCREMENTAR_PESO:
				// leer peticion
				peticion = (PetIncrementarPeso) chIncrementarPeso.in().read();

				// tratar petición, y aplazar si no se cumple CPRE
				// o aplazar directamente
				if (peticion.p + pesoContenedor > MAX_P_CONTENEDOR) {
					aplazadas.add(peticion);
				} else {
					pesoContenedor += peticion.p;
					accediendo++;
					peticion.chACK.out().write(null);
				}

				break;
			case NOTIFICAR_SOLTAR:
				// accediendo > 0 (por protocolo de llamada)
				// leer peticion
				chNotificarSoltar.in().read();

				// tratar peticion
				accediendo--;

				break;
			case PREPARAR_SUSTITUCION:
				// estado == Estado.SUSTITUIBLE && accediendo == 0
				// leer peticion
				chPrepararSustitucion.in().read();

				// tratar peticion
				estado = Estado.SUSTITUYENDO;

				break;
			case NOTIFICAR_SUSTITUCION:
				// estado == Estado.SUSTITUYENDO && accediendo == 0
				// leer peticion
				chNotificarSustitucion.in().read();

				// tratar peticion
				pesoContenedor = 0;
				estado = Estado.LISTO;

				break;
			} // switch

			// tratamiento de peticiones aplazadas

			// Primero comprobamos que el estado sea distinto de sustituyendo, es decir, que
			// realmente podremos desbloquear alguna peticion, en caso contrario simplemente
			// avanzamos al final del bucle de servicio.
			// Para ver si realmente podemos desbloquear alguna peticion cogemos la primera
			// que haya en cola, si esta cumple las condiciones necesarias la tratamos, en
			// caso contrario la ponemos de vuelta en la ultima posicion de la lista.
			// Repetimos este proceso tantas veces como elementos hubiera en la lista al
			// empezar el desbloqueo.
			if (estado != Estado.SUSTITUYENDO) {
				int i = 0;
				int elementos = aplazadas.size();
				while (i < elementos) {
					peticion = aplazadas.poll();
					if (peticion.p + pesoContenedor <= MAX_P_CONTENEDOR) {
						pesoContenedor += peticion.p;
						accediendo++;
						peticion.chACK.out().write(null);
					} else {
						aplazadas.add(peticion);
					}
					i++;
				}
			}

			// si estamos aqui es que no quedan peticiones
			// aplazadas que podrian ser atendidas!!!!!!!!!!!!!!!!!!

		} // bucle servicio
	} // run() SERVER
} // class ControlRecicladoCSP

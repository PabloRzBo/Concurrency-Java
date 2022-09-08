package cc.controlReciclado;

import es.upm.babel.cclib.Monitor;

public final class ControlRecicladoMonitor implements ControlReciclado {
  private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }

  private final int MAX_P_CONTENEDOR;
  private final int MAX_P_GRUA;

  public ControlRecicladoMonitor (int max_p_contenedor,
                                  int max_p_grua) {
    MAX_P_CONTENEDOR = max_p_contenedor;
    MAX_P_GRUA = max_p_grua;
  }

  public void notificarPeso(int p) {
  }

  public void incrementarPeso(int p) {
  }

  public void notificarSoltar() {
  }

  public void prepararSustitucion() {
  }

  public void notificarSustitucion() {
  }
}

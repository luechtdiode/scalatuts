package ch.seidel.pdm.system;

import java.util.List;
import java.util.Map;

public interface Persistence<A, W> {
  public void saveAbos(Map<String, A> abos);
  public Map<String, A> loadAbos();
  public void saveQueue(String aboId, List<W> queue);
  public List<W> loadQueue(String aboId);
}

package ch.seidel.pdm.client;

public interface WorkerMethod<T> {
  public void doWork(T work);
  public boolean isWorking();
}

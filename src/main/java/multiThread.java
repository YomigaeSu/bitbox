import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class multiThread{
	private static class SomeTask implements Runnable
	{
	  @Override
	  public void run()
	  {
			System.out.println("a");
	  }
	}
	
	private static class SomeTask2 implements Runnable
	{
	  @Override
	  public void run()
	  {
			System.out.println("b");
	  }
	}

	public static void main(String[] args)
	{
	  ExecutorService executor = Executors.newCachedThreadPool();
	  executor.execute(new SomeTask());
	  executor.execute(new SomeTask2());
	}
}
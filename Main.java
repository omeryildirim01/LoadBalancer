import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Omer Yildirim
 * on 13.01.2021
 */

class Provider {
    String identifier;
    boolean alive;
    int checkedCount = 0;

    public Provider(String identifier) {
        this.identifier = identifier;
        alive = true;
    }

    public String get() {
        return identifier;
    }

    public boolean isAlive() {
        return alive;
    }

    public void exclude() {
        alive = false;
    }

    public void include() {
        alive = true;
    }
}

class ProviderException extends Exception {
    public ProviderException(String errorMessage) {
        super(errorMessage);
    }
}

abstract class LoadBalancer {
    final List<Provider> providers;
    final int heartBeatChecker = 15;
    final int parallelRequestY = 50;

    public LoadBalancer(List<Provider> providerList) throws ProviderException {
        if (providerList == null || providerList.size() > 10)
            throw new ProviderException("ERROR_CODE:001");
        this.providers = Collections.unmodifiableList(providerList);
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        Runnable taskChecker = new Runnable() {
            public void run() {
                System.out.println("Checking..");
                for (Provider provider : providers) {
                    if (!provider.isAlive()) {
                        provider.checkedCount++;
                        // if checked 2 times , then including
                        if (provider.checkedCount > 2) {
                            provider.include();
                            provider.checkedCount = 0;
                        }
                    }
                }
            }
        };
        scheduledExecutorService.schedule(taskChecker, heartBeatChecker, TimeUnit.SECONDS);
    }

    public void excludeProvider(String identifier) {
        Optional<Provider> provider = providers.stream().filter(p -> p.identifier.equals(identifier)).findFirst();
        if (provider.isPresent()) {
            provider.get().exclude();
        }
    }

    public void includeProvider(String identifier) {
        Optional<Provider> provider = providers.stream().filter(p -> p.identifier.equals(identifier)).findFirst();
        if (provider.isPresent()) {
            provider.get().include();
        }
    }

    public int getClusterCapacityLimit() {
        return providers.stream().filter(p -> p.isAlive()).collect(Collectors.toList()).size() * parallelRequestY;
    }

    abstract Provider get();

    private static void simulate(LoadBalancer loadBalancer) {
        int numberOfCalls = loadBalancer.getClusterCapacityLimit();
        IntStream.range(0, numberOfCalls).parallel().forEach(i -> System.out.println("Request: " + loadBalancer.get().identifier + " --- [" + i + "]--- [Thread: " + Thread.currentThread().getName() + "]"));
    }
}

class RandomLoadBalancer extends LoadBalancer {
    public RandomLoadBalancer(List<Provider> providerList) throws ProviderException {
        super(providerList);
    }

    @Override
    public Provider get() {
        Random random = new Random();
        List<Provider> aliveProviders = providers.stream().filter(p -> p.isAlive()).collect(Collectors.toList());
        return aliveProviders.get(random.nextInt(aliveProviders.size()));
    }
}

class RoundRobinLoadBalancer extends LoadBalancer {
    private int counter = 0;
    private final ReentrantLock lock;

    public RoundRobinLoadBalancer(List<Provider> providerList) throws ProviderException {
        super(providerList);
        lock = new ReentrantLock();
    }

    @Override
    public Provider get() {
        lock.lock();
        try {
            Provider provider = providers.stream().filter(p -> p.isAlive()).collect(Collectors.toList()).get(counter);
            counter += 1;
            if (counter == providers.stream().filter(p -> p.isAlive()).collect(Collectors.toList()).size() || counter > providers.stream().filter(p -> p.isAlive()).collect(Collectors.toList()).size()) {
                counter = 0;
            }
            return provider;
        } finally {
            lock.unlock();
        }
    }
}

public class Main{

}
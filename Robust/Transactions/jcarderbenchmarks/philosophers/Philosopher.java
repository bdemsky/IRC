

public class Philosopher extends Thread {
    private final Chopstick mLeftChopstick;
    private final Chopstick mRightChopstick;
    int id;
    

    public Philosopher(String name, int id,
                       Chopstick leftChopstick,
                       Chopstick rightChopstick) {
        super(name);
        this.id = id;
        mLeftChopstick = leftChopstick;
        mRightChopstick = rightChopstick;
    }

    public void run() {
        long endTime = System.currentTimeMillis() + 100;
        int i =0;
        //while (endTime > System.currentTimeMillis()) {
        while(i<100)
        {
            pickUpSticksAndEat();
            i++;
        }
    }

    private void pickUpSticksAndEat() {
        if (id%2 == 0)
            synchronized (mLeftChopstick) {
                synchronized (mRightChopstick) {
                    System.out.println(getName() + " is eating.");
                }
            }
        else 
            synchronized (mRightChopstick) {
                synchronized (mLeftChopstick) {
                    System.out.println(getName() + " is eating.");
                }
            }
    }
}

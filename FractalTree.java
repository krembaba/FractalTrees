import javax.swing.*;
import java.awt.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FractalTree extends Canvas {
    private static boolean slowMode;
    private static ExecutorService exec;
    
    private static final int QUEUE_CAPACITY = 1000;
    private static final BlockingQueue<LineTask> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    
    private static class LineTask{
        final int x1, y1, x2, y2;
        final Color color;
        
        LineTask (int x1,int y1,int x2,int y2, Color color){
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
        }
    }
    
    private static class TaskCounter{
        private int pending = 0;
        
        public synchronized void taskSubmitted(){
            pending++;
        }
        
        public synchronized void taskFinished(){
            pending--;
            if(pending == 0){
                notifyAll();
            }
        }
        
        public synchronized void awaitZero() throws InterruptedException{
            while (pending > 0){
                wait();
            }
        }
    }
    
    private static final TaskCounter taskCounter = new TaskCounter();

    public void makeFractalTree(Graphics g, int x, int y, int angle, int height) {

        if (slowMode) {
            try {Thread.sleep(100);}
            catch (InterruptedException ie) {ie.printStackTrace();}
        }

        if (height == 0) return;
        
        double length = height * 10.0;
        double rad = Math.toRadians(angle);
        
        
        int x2 = x + (int)(Math.cos(rad) * length);
        int y2 = y + (int)(Math.sin(rad) * length);
        
        Color c = (height <= 2) ? Color.GREEN : Color.BLACK;
        
        try {
            queue.put(new LineTask(x,y,x2,y2,c));
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        
        int delta = 20;
        
        taskCounter.taskSubmitted();
        
        exec.submit(() -> {
            try {
                makeFractalTree(g,x2,y2,angle - delta,height - 1);
            }finally{
                taskCounter.taskFinished();
            }
        });
        
        makeFractalTree(g,x2,y2,angle + delta,height - 1);
    }
    
    public void startDrawingLoop(){
        Graphics g = this.getGraphics();
        if(g == null){
            return;
        }
        
        while(true){
            try {
                LineTask task = queue.take();
                g.setColor(task.color);
                g.drawLine(task.x1,task.y1,task.x2,task.y2);
            }catch (InterruptedException e){
                e.printStackTrace();
                break;
            }
        }
        
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    public static void main(String args[]) {

        
        slowMode = args.length != 0 && Boolean.parseBoolean(args[0]);

        
        FractalTree tree = new FractalTree();
        JFrame frame = new JFrame();
        frame.setSize(800,600);
        frame.setVisible(true);
        frame.add(tree);
        
        int par = Math.min(128, Runtime.getRuntime().availableProcessors());
        exec = Executors.newFixedThreadPool(par);
        
        SwingUtilities.invokeLater(() -> tree.startDrawingLoop());
        
        int startX = frame.getWidth() / 2;
        int startY = frame.getHeight() - 100;
        int startAngle = -90;
        int maxHeight = 10;
        
        taskCounter.taskSubmitted();
        exec.submit(() -> {
            try {
                tree.makeFractalTree(null, startX, startY, startAngle, maxHeight);
            }finally{
                taskCounter.taskFinished();
            }
        });
        
        try {
            taskCounter.awaitZero();
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        
        exec.shutdown();
    
        System.out.println("Main has finished");
    }
}

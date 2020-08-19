package logic;

import loops.GameLoop;
import constants.Constants;
import sound.SoundPlayer;
import sprites.*;
import structure.Maze;
import structure.MazeManager;
import ui.GameMainFrame;
import ui.GamePanel;

import javax.swing.Timer;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import static java.awt.event.KeyEvent.VK_ENTER;
import static sound.Sound.*;
import static sprites.State.EATEN;
import static sprites.State.FRIGHTENED;

public class GameLogic {

    private GameMainFrame frame;                    // Riferimento al nostro JFrame
    private GamePanel gamePanel;                    // Riferimento al nostro JPanel
    private Pacman pacman;                          // Oggetto che rappresenta Pacman
    private Maze maze;
    private ArrayList<Ghost> ghosts;                // ArrayList che contiene i fantasmi
    private boolean inGame;                         // Comunica ad alcuni controlli se in gioco è attivo o meno
    private boolean munch;                          // Gestisce i suoni alternati "waka-waka"
    private boolean pacmanDead;                     // Comunica se Pacman era morto nel ciclo prcedente
    private boolean pacmanStart;                    // Comunica che la partita si è avviata (passati 4 sec.)
    private Timer timer;                            // Timer che scandisce i cicli update-repaint
    private long startTime;                         // Orario in cui è inizata la partita (4 sec. inclusi)
    private long portalTime;                        // Orario dell'ultima volta che si è attivato un portale
    private int level;                              // Livello del gioco
    private int lifeCounter;                        // Prossimo migliaio in cui dovrà scattare una vita extra
    private int consecutiveGhosts;                  // Conta quanti fantasmi si sono mangiati dopo una singola PowerPill
    private String readyString;                     // Stringhe che riportano i valori da stampare
    private String gameOverString;                  // nelle label del GamePanel
    private String levelString;
    private String highScoreString;
    private String livesNumString;

    public GameLogic(GameMainFrame frame, GamePanel gamePanel, int level, int highScore, int lives){
        this.frame = frame;
        this.gamePanel = gamePanel;
        initializeVariables(level,highScore,lives);
    }

    private void initializeVariables(int level, int highScore, int lives) {
        // Richiamiamo il metodo di MazeManager per popolare il labirinto di sprite statici (pillole, portali, frutta)
        this.maze = MazeManager.populateMaze();
        this.lifeCounter = 1;
        this.level = level;
        this.pacman = new Pacman();
        this.pacman.setLives(lives);
        // System.out.println(level);
        this.inGame = true;
        this.pacmanStart = false;
        // Oggetto che farà chiamare doOneLoop() ogni tot millisecondi
        this.timer = new Timer(Constants.GAME_SPEED,new GameLoop(this));
        this.timer.start();
        this.munch = true;
        SoundPlayer.stopAll();
        SoundPlayer.playMusic(GAME_START);
        this.startTime = this.portalTime = System.currentTimeMillis();
        this.maze.setGameStart();
        this.ghosts = new ArrayList<>();
        Pinky pinky = new Pinky(this.pacman);
        Blinky blinky = new Blinky(this.pacman);
        Inky inky = new Inky(this.pacman,blinky);
        Clyde clyde = new Clyde(this.pacman);
        this.ghosts.add(clyde);
        this.ghosts.add(inky);
        this.ghosts.add(pinky);
        this.ghosts.add(blinky);
        this.levelString = new String("");
        this.highScoreString = new String("High Score: "+highScore);
        this.readyString = new String("Ready!");
        this.gameOverString = new String("");
        this.livesNumString = new String("");
        // Sceglie che frutto mostrare in questo livello
        this.maze.chooseFruit(level);
    }

    private void restartLevel(){
        this.startTime = System.currentTimeMillis();
        this.portalTime = System.currentTimeMillis();
        this.pacmanStart = false;
        SoundPlayer.stopAll();
        levelString = "";
        readyString= "Ready!";
        System.out.println(level);
        this.inGame = true;
        this.munch = true;
        this.maze.setGameStart();
        this.pacman.returnToSpawnPoint();
        for(Ghost ghost : this.ghosts) {
            ghost.returnToSpawnPoint(this.level);
            ghost.setPausedTime(0);
        }
        System.gc();
        timer.start();
    }

    public void doOneLoop() {
        this.gamePanel.requestFocus();
        update();
        if(inGame){
            this.gamePanel.repaint();
        }else{
            if(timer.isRunning()){
                timer.stop();
            }
        }
    }

    private void update() {
        if(this.pacmanStart && this.inGame){
            boolean frightened = false;
            boolean eaten = false;
            this.pacman.move();
            for(Ghost ghost : this.ghosts) {
                ghost.move();
                if(ghost.getState() == FRIGHTENED){
                    frightened = true;
                }
                if(ghost.getState() == EATEN){
                    eaten = true;
                }
            }
            checkCollision();
            killPacman();
            if(!this.pacman.isDead() && this.pacmanStart){
                this.playBackgroundMusic(frightened,eaten);
            }
            if(this.maze.getAlivePills() == 0){
                SoundPlayer.stopAll();
                endGame();
            }

        }else if(System.currentTimeMillis() >= (this.startTime + 4*1000)) {
            // Se sono passati 4 secondi, il gioco inizia
            SoundPlayer.removeMusic(DEATH);
            SoundPlayer.removeMusic(GAME_START);
            this.pacmanStart=true;
            readyString = "";
            levelString = "Level: "+level;
            for(Ghost ghost : this.ghosts) {
                ghost.getTimer().start();
            }
            this.maze.setGameStart();
        }
    }

    public void checkCollision(){
        collisionFruit();
        getExtraLife();
        collisionPortals();
        collisionPowerPills();
        collisionPills();
        collisionGhosts();
    }

    private void collisionFruit() {
        Fruit f = this.maze.getFruit();
        if(f != null){
            if(MazeManager.checkCollision(pacman,f)){
                f.setDead(true);
                this.pacman.addPoints(f.getPoints());
                SoundPlayer.playEffect(EAT_FRUIT);
            }
        }
    }

    private void getExtraLife(){
        if(this.pacman.getPoints() >= 10000 * this.lifeCounter){
            this.pacman.increaseLives();
            this.lifeCounter++;
        }
    }

    private void collisionPortals() {
        Portal bluePortal = this.maze.getBluePortal();
        Portal redPortal = this.maze.getRedPortal();
        //System.out.println("Le coordinate del rosso sono: "+bluePortal.getOther().getX()+" e "+bluePortal.getOther().getY());
        //System.out.println("Le coordinate del blu sono: "+redPortal.getOther().getX()+" e "+redPortal.getOther().getY());
        if(System.currentTimeMillis() >= (this.portalTime + 400) && !this.pacman.isDead()){
            if(MazeManager.checkCollision(pacman,bluePortal)){
                teleport(pacman,bluePortal);
            }else if(MazeManager.checkCollision(pacman,redPortal)){
                teleport(pacman,redPortal);
            }else{
                for(Ghost ghost : this.ghosts) {
                    if (MazeManager.checkCollision(ghost, bluePortal)) {
                        teleport(ghost, bluePortal);
                    } else if (MazeManager.checkCollision(ghost, redPortal)) {
                        teleport(ghost, redPortal);
                    }
                }
            }
        }
    }

    private void teleport(Sprite a, Portal p){
        a.setX(p.getOther().getX());
        a.setY(p.getOther().getY());
        if(p.getColor().equals("BLUE")){
            SoundPlayer.playEffect(BLUE_PORTAL_SOUND);
        }else{
            SoundPlayer.playEffect(RED_PORTAL_SOUND);
        }
        this.portalTime = System.currentTimeMillis();
    }

    private void collisionPowerPills() {
        for(int i = 0; i< this.maze.getPowerPillsNum(); i++){
            PowerPill pp = this.maze.getPowerPill(i);
            // rimuovere le pill direttamente dall'ArrayList causava una fastidiosa intermittenza delle altre
            if(MazeManager.checkCollision(pacman,pp)){
                if(!pp.isDead()){
                    this.maze.removeAlivePowerPill();
                    this.pacman.addPoints(pp.getPoints());
                    this.consecutiveGhosts = 0;
                    for(Ghost ghost : this.ghosts) {
                        if (ghost.getState() != EATEN) {
                            ghost.becomeFrightened();
                            System.out.println("Passo a frightened");
                        }
                    }
                }
                pp.setDead(true);
            }
        }
    }

    private void collisionPills() {
        for(int i = 0; i< this.maze.getPillsNum(); i++){
            Pill p = this.maze.getPill(i);
            // rimuovere le pill direttamente dall'ArrayList causava una fastidiosa intermittenza delle altre
            if(MazeManager.checkCollision(pacman,p)){
                if(!p.isDead()){
                    this.maze.removeAlivePill();
                    this.pacman.addPoints(p.getPoints());
                    if(munch){
                        SoundPlayer.playEffect(MUNCH_1);
                        munch = false;
                    } else {
                        SoundPlayer.playEffect(MUNCH_2);
                        munch = true;
                    }
                }
                p.setDead(true);
            }
        }
    }

    private void killPacman() {
        // Se Pacman era morto nell'ultima rilevazione (lo scorso ciclo)
        if(pacmanDead){
            // Se ora Pacman ha finito l'animazione di morte ed è tornato vivo...
            if(!this.pacman.isDead()){
                pacmanDead = false;
                // Scaliamo una vita
                this.pacman.decreaseLives();
                // Se le vite sono a 0 Game Over, altrimenti riparte il livello
                if (this.pacman.getLives() == 0) {
                    makeGameOver();
                }else{
                    restartLevel();
                }
            }
        }
    }

    private void collisionGhosts() {
        for(Ghost ghost : this.ghosts){
            if(!this.pacman.isDead()) {
                if (MazeManager.checkCollision(pacman, ghost)) {
                    switch (ghost.getState()) {
                        case CHASE:
                        case SCATTER:
                            // fermare tutti i suoni
                            SoundPlayer.stopAll();
                            SoundPlayer.playEffect(DEATH);
                            // rendiamo fantasmi invisibili
                            // animazione + suono morte
                            this.pacman.setDead(true);
                            this.pacmanDead = true;
                            break;
                        case FRIGHTENED:
                            ghost.becomeEaten();
                            SoundPlayer.playEffect(EAT_GHOST);
                            this.pacman.addPoints(ghost.getPoints() * (2 ^ this.consecutiveGhosts));
                            this.consecutiveGhosts++;
                            break;
                    }
                }
            }
        }
    }

    public void makeGameOver(){
        levelString = "";
        gameOverString = "Game Over!";
        this.gamePanel.showGameOver();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        restartApplication();
    }

    public void restartApplication() {
        this.frame.writeHighScore(this.pacman.getPoints());
        this.timer.stop();
        this.pacman.getTimer().stop();
        for(Ghost ghost : this.ghosts) {
            ghost.getTimer().stop();
        }
        this.pacman.setDead(true);
        this.inGame = false;
        System.gc();
        SoundPlayer.stopAll();
        frame.initializeGameMenu();
    }

    private void endGame(){
        System.out.println("fine livello");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.level++;
        this.frame.writeHighScore(this.pacman.getPoints());
        this.maze.chooseFruit(this.level);
        MazeManager.populateMaze();
        for(int i = 0; i< this.maze.getPillsNum(); i++){
            this.maze.getPill(i).setDead(false);
        }
        restartLevel();
    }

    public void keyPressed(KeyEvent e) {
        if(inGame){
            this.pacman.keyPressed(e);
            int keyPressed = e.getKeyCode();
            if(keyPressed == VK_ENTER){
                if(timer.isRunning() && this.pacmanStart){
                    pauseGame();
                } else {
                    resumeGame();
                }
            }
        }
    }

    public void pauseGame() {
        SoundPlayer.stopAll();
        timer.stop();
        for(Ghost ghost : this.ghosts) {
            ghost.getTimer().stop();
            ghost.pause();
        }
        frame.showPauseMenu();
    }

    public void resumeGame(){
        timer.start();
        for(Ghost ghost : this.ghosts) {
            ghost.resume();
            ghost.getTimer().start();
        }
    }

    public void playBackgroundMusic(boolean frightened, boolean eaten) {
        // A seconda delle condizioni dei fantasmi o delle pillole viene riprodotto un loop
        if(eaten){
            SoundPlayer.loopEffect(EATEN_SOUND);
        } else if (frightened){
            SoundPlayer.loopEffect(FRIGHT_SOUND);
        }else if(this.maze.getAlivePills() > this.maze.getPillsNum() * 4/5){
            SoundPlayer.loopEffect(SIREN_1);
        } else if (this.maze.getAlivePills() > this.maze.getPillsNum() * 3/5) {
            SoundPlayer.loopEffect(SIREN_2);
        } else if (this.maze.getAlivePills() > this.maze.getPillsNum() * 2/5) {
            SoundPlayer.loopEffect(SIREN_3);
        } else if (this.maze.getAlivePills() > this.maze.getPillsNum() / 5) {
            SoundPlayer.loopEffect(SIREN_4);
        } else if (this.maze.getAlivePills() > 0) {
            SoundPlayer.loopEffect(SIREN_5);
        } else if (this.maze.getAlivePills() == 0){
            SoundPlayer.stopAll();
        }
    }

    public String getReadyString() {
        return readyString;
    }

    public String getGameOverString() {
        return gameOverString;
    }

    public String getLevelString() {
        return levelString;
    }

    public String getHighScoreString() {
        return highScoreString;
    }

    public String getLivesNumString() {
        return livesNumString;
    }

    public int getLives() {
        return this.pacman.getLives();
    }

    public int getPoints() {
        return this.pacman.getPoints();
    }

    public Pacman getPacman() {
        return pacman;
    }

    public ArrayList<Ghost> getGhosts() {
        return ghosts;
    }

    public Maze getMaze() {
        return maze;
    }
}

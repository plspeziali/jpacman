package controller;

import callbacks.GameEventListener;
import callbacks.GameLoop;
import constants.Constants;
import image.Image;
import image.ImageFactory;
import sound.SoundPlayer;
import spriteManagers.FruitManager;
import sprites.*;
import ui.GameMainFrame;
import ui.GamePanel;
import utility.CoordManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import static java.awt.event.KeyEvent.VK_ENTER;
import static sound.Sound.*;
import static sound.Sound.GAME_START;
import static utility.State.EATEN;
import static utility.State.FRIGHTENED;

public class GameController {

    private GameMainFrame frame;                    // Riferimento al nostro JFrame
    private GamePanel gamePanel;
    private Pacman pacman;                          // Oggetto che rappresenta Pacman
    private ArrayList<Ghost> ghosts;                // ArrayList che contiene i fantasmi
    private boolean inGame;                         // Comunica ad alcuni controlli se in gioco è attivo o meno
    private boolean munch;                          // Gestisce i suoni alternati "waka-waka"
    private boolean pacmanDead;                     // Comunica se Pacman era morto nel ciclo prcedente
    private boolean pacmanStart;                    // Comunica che la partita si è avviata (passati 4 sec.)
    private Timer timer;                            // Timer che scandisce i cicli update-repaint
    private long startTime;                         // Orario in cui è inizata la partita (4 sec. inclusi)
    private long portalTime;                        // Orario dell'ultima volta che si è attivato un portale
    private int level;                              // Livello del gioco
    private int lives;                              // Vite rimanenti
    private int lifeCounter;                        // Prossimo migliaio in cui dovrà scattare una vita extra
    private int consecutiveGhosts;                  // Conta quanti fantasmi si sono mangiati dopo una singola PowerPill
    private String readyString;
    private String gameOverString;
    private String levelString;
    private String highScoreString;
    private String livesNumString;

    public GameController(GameMainFrame frame, GamePanel gamePanel, int level, int highScore, int lives){
        this.frame = frame;
        this.gamePanel = gamePanel;
        initializeVariables(level,highScore,lives);
    }

    private void initializeVariables(int level, int highScore, int lives) {
        CoordManager.populateMaze();
        this.lifeCounter = 1;
        this.level = level;
        this.lives = lives;
        System.out.println(level);
        this.inGame = true;
        this.pacmanStart = false;
        this.timer = new Timer(Constants.GAME_SPEED,new GameLoop(this));
        this.timer.start();
        this.munch = true;
        SoundPlayer.stopAll();
        SoundPlayer.playMusic(GAME_START);
        this.startTime = this.portalTime = System.currentTimeMillis();
        FruitManager.setGameStart();
        this.pacman = new Pacman();
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
        FruitManager.initialize();
        FruitManager.chooseFruit(level);
    }

    private void restartLevel(){
        SoundPlayer.stopAll();
        this.pacmanStart = false;
        levelString = "";
        readyString= "Ready!";
        System.out.println(level);
        this.inGame = true;
        this.munch = true;
        this.startTime = System.currentTimeMillis();
        this.portalTime = System.currentTimeMillis();
        FruitManager.setGameStart();
        this.pacman.returnToSpawnPoint();
        for(Ghost ghost : this.ghosts) {
            ghost.returnToSpawnPoint(this.level);
            ghost.setPausedTime(0);
        }
        System.gc();
        timer.start();
    }

    private void collisionFruit() {
        Fruit f = FruitManager.getFruit();
        if(f != null){
            if(CoordManager.checkCollision(pacman,f)){
                f.setDead(true);
                this.pacman.addPoints(f.getPoints());
                SoundPlayer.playEffect(EAT_FRUIT);
            }
        }
    }

    private void getExtraLife(){
        if(this.pacman.getPoints() >= 10000 * this.lifeCounter){
            this.lives++;
            this.lifeCounter++;
        }
    }

    private void collisionPortals() {
        Portal bluePortal = CoordManager.getMaze().getBluePortal();
        Portal redPortal = CoordManager.getMaze().getRedPortal();
        //System.out.println("Le coordinate del rosso sono: "+bluePortal.getOther().getX()+" e "+bluePortal.getOther().getY());
        //System.out.println("Le coordinate del blu sono: "+redPortal.getOther().getX()+" e "+redPortal.getOther().getY());
        if(System.currentTimeMillis() >= (this.portalTime + 400) && !this.pacman.isDead()){
            if(CoordManager.checkCollision(pacman,bluePortal)){
                teleport(pacman,bluePortal);
            }else if(CoordManager.checkCollision(pacman,redPortal)){
                teleport(pacman,redPortal);
            }else{
                for(Ghost ghost : this.ghosts) {
                    if (CoordManager.checkCollision(ghost, bluePortal)) {
                        teleport(ghost, bluePortal);
                    } else if (CoordManager.checkCollision(ghost, redPortal)) {
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
        for(int i = 0; i< CoordManager.getMaze().getPowerPillsNum(); i++){
            PowerPill pp = CoordManager.getMaze().getPowerPill(i);
            // rimuovere le pill direttamente dall'ArrayList causava una fastidiosa intermittenza delle altre
            if(CoordManager.checkCollision(pacman,pp)){
                if(!pp.isDead()){
                    CoordManager.getMaze().removeAlivePowerPill();
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
        for(int i = 0; i< CoordManager.getMaze().getPillsNum(); i++){
            Pill p = CoordManager.getMaze().getPill(i);
            // rimuovere le pill direttamente dall'ArrayList causava una fastidiosa intermittenza delle altre
            if(CoordManager.checkCollision(pacman,p)){
                if(!p.isDead()){
                    CoordManager.getMaze().removeAlivePill();
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
                this.lives--;
                // Se le vite sono a 0 Game Over, altrimenti riparte il livello
                if (this.lives == 0) {
                    makeGameOver();
                }else{
                    restartLevel();
                }
            }
        }
    }

    private void collisionGhosts(Graphics g) {
        for(Ghost ghost : this.ghosts){
            if(!this.pacman.isDead()) {
                if (CoordManager.checkCollision(pacman, ghost)) {
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
        this.gameOverLabel.paintImmediately(this.gameOverLabel.getVisibleRect());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        restartApplication();
    }

    public void restartApplication() {
        int highScore = this.frame.writeHighScore(this.pacman.getPoints());
        this.highScoreLabel.setText("High Score: "+highScore);
        this.gameEventListener = null;
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
            if(!this.pacman.isDead()){
                SoundPlayer.playBackgroundMusic(frightened,eaten);
            }
            if(CoordManager.getMaze().getAlivePills() == 0){
                SoundPlayer.stopAll();
                endGame();
            }

        }else{
            if(System.currentTimeMillis() >= (this.startTime + 1*4000)) { //multiply by 1000 to get milliseconds
                SoundPlayer.removeMusic(DEATH);
                SoundPlayer.removeMusic(GAME_START);
                this.pacmanStart=true;
                readyLabel.setText("");
                levelLabel.setText("Level: "+level);
                for(Ghost ghost : this.ghosts) {
                    ghost.getTimer().start();
                }
                FruitManager.setGameStart();
            }
        }
    }

    private void endGame(){
        System.out.println("fine livello");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.level++;
        int highScore = this.frame.writeHighScore(this.pacman.getPoints());
        this.highScoreLabel.setText("High Score: "+highScore);
        FruitManager.chooseFruit(this.level);
        CoordManager.populateMaze();
        for(int i = 0; i< CoordManager.getMaze().getPillsNum(); i++){
            CoordManager.getMaze().getPill(i).setDead(false);
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
        return lives;
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
}

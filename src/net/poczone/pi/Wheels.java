package net.poczone.pi;

import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class Wheels {
	private static final int LEFT_PIN = 27;
	private static final int RIGHT_PIN = 17;

	private static final int MIDDLE_SIGNAL = 1500;
	private static final int MAX_SPEED = 800;
	private static final int MIN_SIGNAL = MIDDLE_SIGNAL - MAX_SPEED;
	private static final int MAX_SIGNAL = MIDDLE_SIGNAL + MAX_SPEED;

	private static final int AHEAD_SPEED = 400;
	private static final int AHEAD_SINGLE_WHEEL_REVOLUTION_MILLIS = 4880 / 10;
	private static final int TURN_SPEED = 100;
	private static final int TURN_360_MILLIS = 2720;

	private static final double WHEEL_RADIUS_MM = 30;
	private static final double SINGLE_WHEEL_TURN_MM = 2 * Math.PI * WHEEL_RADIUS_MM;

	private Timer tickTimer = new Timer();
	private TimerTask tickTask = null;
	private LinkedList<RobotCommand> commandQueue = new LinkedList<>();

	public synchronized boolean add(String commandsString) {
		try {
			if (commandsString == null || commandsString.trim().isEmpty()) {
				return true;
			}

			boolean executeRightAway = commandQueue.isEmpty();

			String[] commands = commandsString.trim().split(" +");
			for (int i = 0; i < commands.length; i++) {
				String command = commands[i].toLowerCase();

				if ("clear".equals(command) || "c".equals(command)) {
					clear();
					executeRightAway = true;
				} else if ("wait".equals(command) || "w".equals(command)) {
					i++;
					int millis = Integer.parseInt(commands[i]);
					commandQueue.add(new WaitCommand(millis));
				} else if ("left".equals(command) || "l".equals(command)) {
					i++;
					int speed = Integer.parseInt(commands[i]);
					commandQueue.add(new SetWheelSpeedCommand(LEFT_PIN, speed));
				} else if ("right".equals(command) || "r".equals(command)) {
					i++;
					int speed = -Integer.parseInt(commands[i]);
					commandQueue.add(new SetWheelSpeedCommand(RIGHT_PIN, speed));
				} else if ("stop".equals(command) || "s".equals(command)) {
					stop();
				} else if ("turn".equals(command) || "t".equals(command)) {
					i++;
					double angleDeg = Double.parseDouble(commands[i]);
					commandQueue.add(new TurnCommand(angleDeg));
					stop();
				} else if ("ahead".equals(command) || "a".equals(command)) {
					i++;
					int mm = Integer.parseInt(commands[i]);
					commandQueue.add(new AheadCommand(mm));
					stop();
				} else if ("move".equals(command) || "m".equals(command)) {
					i++;
					int dx = Integer.parseInt(commands[i]);
					i++;
					int dy = Integer.parseInt(commands[i]);

					if (dx != 0 || dy != 0) {
						double angleDeg = Math.atan2(dx, dy) * 180 / Math.PI;
						int distance = (int) Math.round(Math.hypot(dx, dy));
						commandQueue.add(new TurnCommand(angleDeg));
						commandQueue.add(new AheadCommand(distance));
					}
					stop();
				} else {
					throw new IllegalArgumentException();
				}
			}

			if (executeRightAway) {
				tick();
			}
			return true;
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) {
			clear();
			return false;
		}
	}

	private void stop() {
		commandQueue.add(new SetWheelSpeedCommand(LEFT_PIN, 0));
		commandQueue.add(new SetWheelSpeedCommand(RIGHT_PIN, 0));
	}

	private void clear() {
		if (tickTask != null) {
			tickTask.cancel();
			tickTask = null;
		}
		commandQueue.clear();
	}

	private synchronized void tick() {
		while (!commandQueue.isEmpty()) {
			RobotCommand next = commandQueue.removeFirst();

			next.run();

			int waitMillis = next.getWaitMillis();
			if (waitMillis > 0) {
				scheduleTick(waitMillis);
				return;
			}
		}
	}

	private void scheduleTick(int waitMillis) {
		tickTask = new TimerTask() {
			@Override
			public void run() {
				tickTask = null;
				tick();
			}
		};
		try {
			tickTimer.schedule(tickTask, waitMillis);
		} catch (IllegalStateException e) {
			// System may have killed the timer thread
			tickTimer = new Timer();
			tickTimer.schedule(tickTask, waitMillis);
		}
	}

	public interface RobotCommand {
		public boolean run();

		public int getWaitMillis();
	}

	private class SetWheelSpeedCommand implements RobotCommand {
		private int gpioPin;
		private int value;

		public SetWheelSpeedCommand(int gpioPin, int value) {
			this.gpioPin = gpioPin;
			this.value = value;
		}

		public boolean run() {
			try {
				int speed = value != 0 ? Math.max(MIN_SIGNAL, Math.min(MIDDLE_SIGNAL + value, MAX_SIGNAL)) : 0;
				return new ProcessBuilder("pigs", "m", "" + gpioPin, "w", "servo", "" + gpioPin, "" + speed).start()
						.waitFor() == 0;
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public int getWaitMillis() {
			return 0;
		}
	}

	private class WaitCommand implements RobotCommand {
		private int millis;

		public WaitCommand(int millis) {
			this.millis = millis;
		}

		@Override
		public boolean run() {
			return true;
		}

		@Override
		public int getWaitMillis() {
			return millis;
		}
	}

	private class TurnCommand implements RobotCommand {
		private double angleDeg;

		public TurnCommand(double angleDeg) {
			this.angleDeg = angleDeg;
		}

		@Override
		public boolean run() {
			int factor = angleDeg > 0 ? 1 : -1;
			return new SetWheelSpeedCommand(LEFT_PIN, factor * TURN_SPEED).run()
					& new SetWheelSpeedCommand(RIGHT_PIN, factor * TURN_SPEED).run();
		}

		@Override
		public int getWaitMillis() {
			return (int) Math.round(Math.abs(angleDeg * TURN_360_MILLIS / 360));
		}
	}

	private class AheadCommand implements RobotCommand {
		private int mm;

		public AheadCommand(int mm) {
			this.mm = mm;
		}

		@Override
		public boolean run() {
			int factor = mm > 0 ? 1 : -1;
			return new SetWheelSpeedCommand(LEFT_PIN, factor * AHEAD_SPEED).run()
					&& new SetWheelSpeedCommand(RIGHT_PIN, -factor * AHEAD_SPEED).run();
		}

		@Override
		public int getWaitMillis() {
			return (int) Math.round(Math.abs(AHEAD_SINGLE_WHEEL_REVOLUTION_MILLIS * mm / SINGLE_WHEEL_TURN_MM));
		}
	}

	public void shutdown() {
		tickTimer.cancel();
	}
}

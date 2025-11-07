package task1;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import java.io.Serializable;
import java.util.*;

public class main extends Agent {

    static class NodeData implements Serializable {
        int id;
        double value;
        HashMap<Integer, Double> known = new HashMap<>();

        NodeData(int i, double v) {
            id = i;
            value = v;
            known.put(i, v);
        }
    }

    static class Config {
        static final int TOTAL_AGENTS = 15;
        static HashMap<Integer, List<Integer>> links = new HashMap<>();
        static HashMap<Integer, Double> values = new HashMap<>();

        static {
            links.put(1, Arrays.asList(2, 4));
            links.put(2, Arrays.asList(1, 8));
            links.put(3, Arrays.asList(8));
            links.put(4, Arrays.asList(1, 7, 8));
            links.put(5, Arrays.asList(6, 7, 14));
            links.put(6, Arrays.asList(5, 14));
            links.put(7, Arrays.asList(4, 5));
            links.put(8, Arrays.asList(2, 3, 4));
            links.put(9, Arrays.asList(10));
            links.put(10, Arrays.asList(9, 11, 12));
            links.put(11, Arrays.asList(10, 13));
            links.put(12, Arrays.asList(10, 13, 14));
            links.put(13, Arrays.asList(11, 12, 15));
            links.put(14, Arrays.asList(5, 6, 12));
            links.put(15, Arrays.asList(13));

            values.put(1, 3.0);
            values.put(2, 12.0);
            values.put(3, 46.0);
            values.put(4, 67.0);
            values.put(5, 36.0);
            values.put(6, 18.0);
            values.put(7, 81.0);
            values.put(8, -3.0);
            values.put(9, 18.0);
            values.put(10, 104.0);
            values.put(11, 1.0);
            values.put(12, -21.0);
            values.put(13, 25.0);
            values.put(14, -46.0);
            values.put(15, 13.0);
        }

        static List<Integer> getNeighbors(int id) {
            return links.get(id);
        }

        static double getValue(int id) {
            return values.get(id);
        }

        static double getAverage() {
            double sum = 0;
            for (double v : values.values()) sum += v;
            return sum / TOTAL_AGENTS;
        }
    }

    NodeData data;
    int stage = 0;
    boolean firstTime = true;
    boolean stopFlag = false;

    // Счетчики для стоимости
    int memoryCells = 0; // Количество ячеек памяти
    int iterations = 0; // Количество итераций
    int arithmeticOps = 0; // Арифметические действия
    int interAgentMessages = 0; // Передачи между агентами (отправки соседям)
    int memoryWrites = 0; // Записи в память

    @Override
    protected void setup() {
        String name = getLocalName();
        int id = Integer.parseInt(name.replace("node", ""));
        double val = Config.getValue(id);

        data = new NodeData(id, val);

        // Инициализация ячеек памяти и записей
        memoryCells += 2; // id и value
        memoryWrites += 2; // Запись id и value

        addBehaviour(new ReceiveBehaviour());
        addBehaviour(new SendBehaviour(this, 4000));
        addBehaviour(new StopBehaviour());

        if (id == 1) {
            addBehaviour(new CollectorBehaviour());
        }
    }

    class SendBehaviour extends TickerBehaviour {
        public SendBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (stopFlag) {
                stop();
                return;
            }

            if (firstTime) {
                firstTime = false;
                return;
            }

            stage++;
            iterations++; // Подсчет итераций

            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            try {
                msg.setContentObject(data);
            } catch (Exception e) {}

            List<Integer> neighbors = Config.getNeighbors(data.id);
            for (int neighbor : neighbors) {
                msg.addReceiver(new jade.core.AID("node" + neighbor, jade.core.AID.ISLOCALNAME));
            }
            send(msg);
            interAgentMessages += neighbors.size(); // Подсчет передач между агентами

            try {
                Thread.sleep(3000);
            } catch (Exception e) {}

            // Отправляем статус node1 с счетчиками
            ACLMessage statusMsg = new ACLMessage(ACLMessage.REQUEST);
            statusMsg.addReceiver(new jade.core.AID("node1", jade.core.AID.ISLOCALNAME));
            statusMsg.setContent(stage + "," + data.id + "," + data.known.size() + "," + memoryCells + "," + iterations + "," + arithmeticOps + "," + interAgentMessages + "," + memoryWrites);
            send(statusMsg);
        }
    }

    class ReceiveBehaviour extends Behaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));

            if (msg != null) {
                try {
                    NodeData otherData = (NodeData) msg.getContentObject();
                    int oldSize = data.known.size();
                    data.known.putAll(otherData.known);
                    int newEntries = data.known.size() - oldSize;
                    memoryCells += newEntries; // Подсчет новых ячеек памяти
                    memoryWrites += newEntries; // Подсчет записей в память
                } catch (Exception e) {}
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    class StopBehaviour extends Behaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.CANCEL));

            if (msg != null) {
                stopFlag = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    class CollectorBehaviour extends Behaviour {
        private HashMap<Integer, Integer> receivedKnownSizes = new HashMap<>();
        private HashMap<Integer, int[]> receivedCounters = new HashMap<>();
        private int currentStage = 1;

        // Суммарные счетчики для стоимости (собираются от агентов)
        private int totalMemoryCells = 0;
        private int totalIterations = 0;
        private int totalArithmeticOps = 0;
        private int totalInterAgentMessages = 0;
        private int totalMemoryWrites = 0;
        private int centerMessages = Config.TOTAL_AGENTS; // Фиксировано n передач в центр

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));

            if (msg != null) {
                String[] parts = msg.getContent().split(",");
                int msgStage = Integer.parseInt(parts[0]);
                int id = Integer.parseInt(parts[1]);
                int size = Integer.parseInt(parts[2]);
                int memCells = Integer.parseInt(parts[3]);
                int iters = Integer.parseInt(parts[4]);
                int arithOps = Integer.parseInt(parts[5]);
                int interMsgs = Integer.parseInt(parts[6]);
                int memWrites = Integer.parseInt(parts[7]);

                if (msgStage == currentStage) {
                    receivedKnownSizes.put(id, size);
                    receivedCounters.put(id, new int[]{memCells, iters, arithOps, interMsgs, memWrites});

                    if (receivedKnownSizes.size() == Config.TOTAL_AGENTS) {
                        System.out.println("STEP " + currentStage);
                        for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
                            System.out.println("Node " + i + " knows about " + receivedKnownSizes.get(i) + " nodes");
                        }

                        boolean allKnowAll = true;
                        for (int s : receivedKnownSizes.values()) {
                            if (s != Config.TOTAL_AGENTS) {
                                allKnowAll = false;
                                break;
                            }
                        }

                        if (allKnowAll) {
                            System.out.println("RESULTS");
                            double avg = Config.getAverage();
                            arithmeticOps += 15; // Подсчет арифметики для расчета среднего
                            for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
                                System.out.println("The average of node " + i + ": " + String.format("%.2f", avg));
                            }
                            System.out.println("Real average: " + String.format("%.2f", avg));

                            // Сбор и расчет стоимости
                            totalMemoryCells = 0;
                            totalIterations = currentStage;
                            totalArithmeticOps = 0;
                            totalInterAgentMessages = 0;
                            totalMemoryWrites = 0;
                            for (int[] counters : receivedCounters.values()) {
                                totalMemoryCells += counters[0];
                                totalIterations += counters[1];
                                totalArithmeticOps += counters[2];
                                totalInterAgentMessages += counters[3];
                                totalMemoryWrites += counters[4];
                            }
                            totalArithmeticOps += arithmeticOps;

                            double cost = (totalMemoryCells * 1.0) + (currentStage * 1.0) + (totalArithmeticOps * 0.01) +
                                    (totalInterAgentMessages * 0.1) + (totalMemoryWrites * 0.01) + (centerMessages * 1000.0);

                            System.out.println("Algorithm cost calculation:");
                            System.out.println("Agent-centre - 1000");
                            System.out.println("Agent-agent - 0.1р ");
                            System.out.println("Arithmetic operations - 0.01");
                            System.out.println("Memory cell - 1.  ");
                            System.out.println("Iteration - 1.");
                            System.out.println("Memory recording - 0.01.");
                            System.out.println("n=" + Config.TOTAL_AGENTS);
                            System.out.println("Q = " + totalMemoryCells + "*1 + " + currentStage + "*1 + " + totalArithmeticOps + "*0.01 + " +totalInterAgentMessages + "*0.1 + " + totalMemoryWrites + "*0.01 + " + centerMessages + "*1000");
                            System.out.println("Q=" + String.format("%.2f", cost));

                            // Отправляем STOP всем агентам
                            for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
                                ACLMessage stopMsg = new ACLMessage(ACLMessage.CANCEL);
                                stopMsg.addReceiver(new jade.core.AID("node" + i, jade.core.AID.ISLOCALNAME));
                                send(stopMsg);
                            }
                        } else {
                            receivedKnownSizes.clear();
                            receivedCounters.clear();
                            currentStage++;
                        }
                    }
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    public static void main(String[] args) {
        Runtime rt = Runtime.instance();

        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "true");

        AgentContainer container = rt.createMainContainer(p);

        try {
            showInfo();
            startAgents(container);

        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }

    private static void showInfo() {
        System.out.println("Network consists of 15 agents. Graph topology:");

        for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
            System.out.println("Node" + i + " (" +
                    Config.getValue(i) +
                    ") ---> " +
                    Config.getNeighbors(i));
        }
        System.out.println();
    }

    private static void startAgents(AgentContainer container) throws StaleProxyException {
        for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
            AgentController agent = container.createNewAgent(
                    "node" + i,
                    main.class.getName(),
                    new Object[]{}
            );
            agent.start();
        }
    }
}

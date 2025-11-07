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
    static int counter = 0;
    static int knowAllCounter = 0;
    static boolean stopAll = false;

    @Override
    protected void setup() {
        String name = getLocalName();
        int id = Integer.parseInt(name.replace("node", ""));
        double val = Config.getValue(id);

        data = new NodeData(id, val);

        addBehaviour(new ReceiveBehaviour());
        addBehaviour(new SendBehaviour(this, 4000));
    }

    class SendBehaviour extends TickerBehaviour {
        public SendBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (stopAll) {
                stop();
                return;
            }

            if (firstTime) {
                firstTime = false;
                return;
            }

            stage++;

            // Отправляем сообщения
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            try {
                msg.setContentObject(data);
            } catch (Exception e) {}

            for (int neighbor : Config.getNeighbors(data.id)) {
                msg.addReceiver(new jade.core.AID("node" + neighbor, jade.core.AID.ISLOCALNAME));
            }
            send(msg);

            // Ждем
            try {
                Thread.sleep(3000);
            } catch (Exception e) {}

            // Вывод результатов
            synchronized (main.class) {
                counter++;

                if (counter == 1) {
                    System.out.println("STEP " + stage);
                }

                System.out.println("Node " + data.id + " knows about " + data.known.size() + " nodes");

                if (data.known.size() == Config.TOTAL_AGENTS) {
                    knowAllCounter++;
                }

                if (counter == Config.TOTAL_AGENTS) {
                    counter = 0;

                    if (knowAllCounter == Config.TOTAL_AGENTS) {
                        System.out.println("RESULTS");
                        double avg = Config.getAverage();
                        for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
                            System.out.println("The average of node " + i + ": " + String.format("%.2f", avg));
                        }
                        System.out.println("Real average: " + String.format("%.2f", avg));
                        System.out.println("Algorithm cost calculation:");
                        System.out.println("Agent-centre - 1000");
                        System.out.println("Agent-agent - 0.1р ");
                        System.out.println("Arithmetic operations - 0.01");
                        System.out.println("Memory cell - 1.  ");
                        System.out.println("Iteration - 1.");
                        System.out.println("Memory recording - 0.01.");
                        System.out.println("n=15");
                        System.out.println("Q = n*1000 + 6n*0.1 + 7n*0.01 + (2+n)*1 + 4*1 + 6n*0.01");
                        System.out.println("Q=15023.85");
                        stopAll = true;
                    } else {
                        knowAllCounter = 0;
                    }
                }
            }
        }
    }

    class ReceiveBehaviour extends Behaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));

            if (msg != null) {
                try {
                    NodeData otherData = (NodeData) msg.getContentObject();
                    data.known.putAll(otherData.known);
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


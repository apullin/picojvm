/**
 * LinkedList — linked list with OOP: new, putfield, getfield, null check.
 *
 * Expected output (as bytes): 3, 30, 20, 10
 *   size=3, then 30→20→10 (LIFO order)
 */
class Node {
    int value;
    Node next;
    Node(int v) { value = v; }
}

public class T38_LinkedList {
    static Node head;
    static int size;

    static void push(int v) {
        Node n = new Node(v);
        n.next = head;
        head = n;
        size++;
    }

    static void printAll() {
        Node cur = head;
        while (cur != null) {
            Native.putchar(cur.value);
            cur = cur.next;
        }
    }

    public static void main(String[] args) {
        push(10);
        push(20);
        push(30);
        Native.putchar(size);  // 3
        printAll();            // 30, 20, 10
        Native.halt();
    }
}

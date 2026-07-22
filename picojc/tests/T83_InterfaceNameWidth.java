class T83NamePadding {
    static int paddingName0;
    static int paddingName1;
    static int paddingName2;
    static int paddingName3;
    static int paddingName4;
    static int paddingName5;
    static int paddingName6;
    static int paddingName7;
    static int paddingName8;
    static int paddingName9;
    static int paddingName10;
    static int paddingName11;
    static int paddingName12;
    static int paddingName13;
    static int paddingName14;
    static int paddingName15;
    static int paddingName16;
    static int paddingName17;
    static int paddingName18;
    static int paddingName19;
    static int paddingName20;
    static int paddingName21;
    static int paddingName22;
    static int paddingName23;
    static int paddingName24;
    static int paddingName25;
    static int paddingName26;
    static int paddingName27;
    static int paddingName28;
    static int paddingName29;
    static int paddingName30;
    static int paddingName31;
    static int paddingName32;
    static int paddingName33;
    static int paddingName34;
    static int paddingName35;
    static int paddingName36;
    static int paddingName37;
    static int paddingName38;
    static int paddingName39;
    static int paddingName40;
    static int paddingName41;
    static int paddingName42;
    static int paddingName43;
    static int paddingName44;
    static int paddingName45;
    static int paddingName46;
    static int paddingName47;
    static int paddingName48;
    static int paddingName49;
    static int paddingName50;
    static int paddingName51;
    static int paddingName52;
    static int paddingName53;
    static int paddingName54;
    static int paddingName55;
    static int paddingName56;
    static int paddingName57;
    static int paddingName58;
    static int paddingName59;
    static int paddingName60;
    static int paddingName61;
    static int paddingName62;
    static int paddingName63;
    static int paddingName64;
    static int paddingName65;
    static int paddingName66;
    static int paddingName67;
    static int paddingName68;
    static int paddingName69;
    static int paddingName70;
    static int paddingName71;
    static int paddingName72;
    static int paddingName73;
    static int paddingName74;
    static int paddingName75;
    static int paddingName76;
    static int paddingName77;
    static int paddingName78;
    static int paddingName79;
    static int paddingName80;
    static int paddingName81;
    static int paddingName82;
    static int paddingName83;
    static int paddingName84;
    static int paddingName85;
    static int paddingName86;
    static int paddingName87;
    static int paddingName88;
    static int paddingName89;
}

interface T83LateInterface {
    int value();
}

class T83LateImplementation implements T83LateInterface {
    public int value() {
        return 7;
    }
}

public class T83_InterfaceNameWidth {
    public static void main(String[] args) {
        T83LateInterface value = new T83LateImplementation();
        Native.putchar(48 + value.value());
        Native.putchar(10);
    }
}

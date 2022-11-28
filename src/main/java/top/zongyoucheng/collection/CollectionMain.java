package top.zongyoucheng.collection;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import com.serotonin.modbus4j.serial.SerialPortWrapper;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Properties;

/**
 * 通过串口解析MODBUS协议
 * @author zongyoucheng
 */
public class CollectionMain {
    // 设定MODBUS网络上从站地址
    private final static int SLAVE_ADDRESS_COM3 = 1;
    private final static int SLAVE_ADDRESS_COM4 = 1;
    //串行波特率
    private final static int BAUD_RATE = 9600;

    public static void main(String[] args) throws Exception {
        Double equipmentA1, equipmentA2, equipmentA3, equipmentA4;
        Double equipmentA5, equipmentA6, equipmentA7, equipmentA8;

        SerialPortWrapper serialParametersCom3 = new top.zongyoucheng.collection.SerialPortWrapperImpl("COM3", BAUD_RATE, 8, 1, 0, 0, 0);
        SerialPortWrapper serialParametersCom4 = new top.zongyoucheng.collection.SerialPortWrapperImpl("COM4", BAUD_RATE, 8, 1, 0, 0, 0);
        /* 创建ModbusFactory工厂实例 */
        ModbusFactory modbusFactory = new ModbusFactory();
        /* 创建ModbusMaster实例 */
        ModbusMaster masterCom3 = modbusFactory.createRtuMaster(serialParametersCom3);
        ModbusMaster masterCom4 = modbusFactory.createRtuMaster(serialParametersCom4);
        //创建Properties对象
        Properties pros = new Properties();
        //读取druid.properties中参数
        pros.load(ClassLoader.getSystemClassLoader().getResourceAsStream("druid.properties"));
        //创建指定参数的Druid连接池
        DataSource source = DruidDataSourceFactory.createDataSource(pros);
        //创建连接实例
        Connection conn = source.getConnection();
        //创建runner实例
        QueryRunner runner = new QueryRunner();
        //创建DecimalFormat实例
        DecimalFormat decimalFormat = new DecimalFormat("#0.000");

        //状态标志位，不能被循环重置
        Integer A1StatusFlag = 0,A2StatusFlag = 0,A3StatusFlag = 0,A6StatusFlag = 0,A7StatusFlag = 0,A8StatusFlag = 0;
        //报警标志位，不能被循环重置
        Integer A1WarningFlag = 0,A2WarningFlag = 0,A3WarningFlag = 0,A6WarningFlag = 0,A7WarningFlag = 0,A8WarningFlag = 0;
        //出铁结束检测标志位，不能被循环重置，用于控制出钢是否结束的检测模块的运行，确保其只运行一次
        Integer A1PourFlag = 0,A2PourFlag = 0,A3PourFlag = 0,A6PourFlag = 0,A7PourFlag = 0,A8PourFlag = 0;

        Long A1castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A2castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A3castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A6castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A7castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A8castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        //初始化状态变量
        Double A1statusLowerMark = 0.1;//已根据历史数据初次设定
        Double A2statusLowerMark = 0.1;//已根据历史数据初次设定
        Double A3statusLowerMark = 0.1;//已根据历史数据初次设定
        Double A6statusLowerMark = 0.1;//已根据历史数据初次设定
        Double A7statusLowerMark = 0.3;//已根据历史数据初次设定
        Double A8statusLowerMark = 0.26;//已根据历史数据初次设定
        Double warningLevel = 2.8;//警戒液位
        //初始化重量变量
        String A1Weight = null;
        String A2Weight = null;
        String A3Weight = null;
        String A6Weight = null;
        String A7Weight = null;
        String A8Weight = null;

        /* 读取液位并显示至数据库 */
        while (true) {
            try {
                //时间测试
                //Long testTime = System.currentTimeMillis();

                masterCom3.init();
                masterCom4.init();
                short[] listCom3 = readHoldingRegistersTest(masterCom3, SLAVE_ADDRESS_COM3, 8, 4);
                short[] listCom4 = readHoldingRegistersTest(masterCom4, SLAVE_ADDRESS_COM4, 8, 4);

                equipmentA1 = (Integer.valueOf(listCom3[0]) - 4000) * 0.000578125;//西出铁口40009寄存器
                equipmentA2 = (Integer.valueOf(listCom3[1]) - 4000) * 0.000578125;//西出铁口40010寄存器
                equipmentA3 = (Integer.valueOf(listCom3[2]) - 4000) * 0.000578125;//西出铁口40011寄存器

                equipmentA6 = (Integer.valueOf(listCom4[0]) - 4000) * 0.000578125;//东出铁口40009寄存器
                equipmentA7 = (Integer.valueOf(listCom4[2]) - 4000) * 0.000578125;//东出铁口40011寄存器
                equipmentA8 = (Integer.valueOf(listCom4[3]) - 4000) * 0.000578125;//东出铁口40012寄存器

                //初始化液位变量并写入当前液位
                String A1Level = decimalFormat.format(equipmentA1);
                String A2Level = decimalFormat.format(equipmentA2);
                String A3Level = decimalFormat.format(equipmentA3);
                String A6Level = decimalFormat.format(equipmentA6);
                String A7Level = decimalFormat.format(equipmentA7);
                String A8Level = decimalFormat.format(equipmentA8);

                //初始化历史液位变量（3分钟前液位，用于判断出钢是否结束）
                Double A1HistoryLevel = null;
                Double A2HistoryLevel = null;
                Double A3HistoryLevel = null;
                Double A6HistoryLevel = null;
                Double A7HistoryLevel = null;
                Double A8HistoryLevel = null;

                //初始化流速流量变量
                String A1Flow = null;
                String A2Flow = null;
                String A3Flow = null;
                String A6Flow = null;
                String A7Flow = null;
                String A8Flow = null;

                //初始化时间变量
                Integer equipmentLocation;//当前铁水包位置
                String castAlertTime;//出铁报警时间，已通过sql自行计算
                String castEndTime;//出铁结束时间
                Double castTotalTime;//出铁合计时间，已通过sql自行计算
                String castTonnage;//最后的出铁吨位

                //液位低于下限且先前A1状态标志位是出铁或满包状态
                if (equipmentA1 <= A1statusLowerMark && A1StatusFlag != 0) {
                    //A1状态标志位 置0
                    A1StatusFlag = 0;
                    //A1报警标志位 置0
                    A1WarningFlag = 0;
                    //调试输出控制台
                    System.out.println("液位低于下限且先前A1状态标志位是出铁或满包状态, A1状态标志位 0，A1报警标志位 0");
                //液位高于下限且先前A1状态标志位是空包状态，空包→出铁
                } else if (equipmentA1 > A1statusLowerMark && A1StatusFlag == 0) {
                    A1castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                    equipmentLocation = 1;
                    System.out.println("当前铁水包位置:" + equipmentLocation + "开始出铁时间由SQL自动载入");
                    String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                    BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                    runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    //A1状态标志位 置出铁
                    A1StatusFlag = 1;
                    //A1出铁结束检测标志位 开启
                    A1PourFlag = 1;
                    //调试输出控制台
                    System.out.println("液位高于下限且先前A1状态标志位是空包状态，空包→出铁, A1状态标志位 1，A1报警标志位 不变, A1出铁结束检测志位 1，A1castStartTime: " + A1castStartTime);
                //先前A1状态标志位不是空包状态且出铁结束检测标志位开启，出铁→满包
                } else if (A1StatusFlag != 0 && A1PourFlag == 1) {
                    //读取历史液位数据，从Level表
                    String sqlHistoryLevel = "SELECT equipment_a1 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 30,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_a1");
                    A1HistoryLevel =  runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    //调试输出控制台
                    //System.out.println("读取历史液位数据，从Level表, A1HistoryLevel: " + A1HistoryLevel);
                    //当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法
                    if ((Double.parseDouble(A1Level)  - A1HistoryLevel) < -0.01) {
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = A1Weight;
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 1;//当前铁水包位置1
                        //System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                        //A1出铁结束检测标志位 关闭
                        A1PourFlag = 0;
                        //调试输出控制台
                        System.out.println("当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，出铁结束检测标志位关闭，更新出铁结束时间，出铁合计时间，出铁吨位. 当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法, castTonnage: " + castTonnage + ", A1Level: " + A1Level + ", castEndTime: " + castEndTime);
                    }
                }
                //液位高于报警（单独判断）且先前A1报警标志位是0，执行报警方法
                if (equipmentA1 >= warningLevel && A1WarningFlag == 0) {
                    //更新出铁报警时间
                    equipmentLocation = 1;//当前铁水包位置1
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocation);
                    //A1报警标志位 置1
                    A1WarningFlag = 1;
                    //A1状态标志位 置满包
                    A1StatusFlag = 2;
                    //调试输出控制台
                    System.out.println("出铁报警, equipmentA1: " + equipmentA1 + ", warningLevel:" + warningLevel + "A1状态标志位 2， A1报警标志位 1");
                }

                //液位低于下限且先前A2状态标志位是出铁或满包状态
                if (equipmentA2 <= A2statusLowerMark && A2StatusFlag != 0) {
                    //A2状态标志位 置0
                    A2StatusFlag = 0;
                    //A2报警标志位 置0
                    A2WarningFlag = 0;
                    //调试输出控制台
                    System.out.println("液位低于下限且先前A2状态标志位是出铁或满包状态, A2状态标志位 0，A2报警标志位 0");
                    //液位高于下限且先前A2状态标志位是空包状态，空包→出铁
                } else if (equipmentA2 > A2statusLowerMark && A2StatusFlag == 0) {
                    A2castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                    equipmentLocation = 2;
                    System.out.println("当前铁水包位置:" + equipmentLocation + "开始出铁时间由SQL自动载入");
                    String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                    BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                    runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    //A2状态标志位 置出铁
                    A2StatusFlag = 1;
                    //A2出铁结束检测标志位 开启
                    A2PourFlag = 1;
                    //调试输出控制台
                    System.out.println("液位高于下限且先前A2状态标志位是空包状态，空包→出铁, A2状态标志位 1，A2报警标志位 不变, A2出铁结束检测志位 1，A2castStartTime: " + A2castStartTime);
                    //先前A2状态标志位不是空包状态且出铁结束检测标志位开启，出铁→满包
                } else if (A2StatusFlag != 0 && A2PourFlag == 1) {
                    //读取历史液位数据，从Level表
                    String sqlHistoryLevel = "SELECT equipment_A2 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 30,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A2");
                    A2HistoryLevel =  runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    //调试输出控制台
                    //System.out.println("读取历史液位数据，从Level表, A2HistoryLevel: " + A2HistoryLevel);
                    //当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法
                    if ((Double.parseDouble(A2Level)  - A2HistoryLevel) < -0.01) {
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = A2Weight;
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 2;//当前铁水包位置2
                        //System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                        //A2出铁结束检测标志位 关闭
                        A2PourFlag = 0;
                        //调试输出控制台
                        System.out.println("当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，出铁结束检测标志位关闭，更新出铁结束时间，出铁合计时间，出铁吨位. 当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法, castTonnage: " + castTonnage + ", A2Level: " + A2Level + ", castEndTime: " + castEndTime);
                    }
                }
                //液位高于报警（单独判断）且先前A2报警标志位是0，执行报警方法
                if (equipmentA2 >= warningLevel && A2WarningFlag == 0) {
                    //更新出铁报警时间
                    equipmentLocation = 2;//当前铁水包位置2
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocation);
                    //A2报警标志位 置1
                    A2WarningFlag = 1;
                    //A2状态标志位 置满包
                    A2StatusFlag = 2;
                    //调试输出控制台
                    System.out.println("出铁报警, equipmentA2: " + equipmentA2 + ", warningLevel:" + warningLevel + "A2状态标志位 2， A2报警标志位 1");
                }

                //液位低于下限且先前A3状态标志位是出铁或满包状态
                if (equipmentA3 <= A3statusLowerMark && A3StatusFlag != 0) {
                    //A3状态标志位 置0
                    A3StatusFlag = 0;
                    //A3报警标志位 置0
                    A3WarningFlag = 0;
                    //调试输出控制台
                    System.out.println("液位低于下限且先前A3状态标志位是出铁或满包状态, A3状态标志位 0，A3报警标志位 0");
                    //液位高于下限且先前A3状态标志位是空包状态，空包→出铁
                } else if (equipmentA3 > A3statusLowerMark && A3StatusFlag == 0) {
                    A3castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                    equipmentLocation = 3;
                    System.out.println("当前铁水包位置:" + equipmentLocation + "开始出铁时间由SQL自动载入");
                    String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                    BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                    runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    //A3状态标志位 置出铁
                    A3StatusFlag = 1;
                    //A3出铁结束检测标志位 开启
                    A3PourFlag = 1;
                    //调试输出控制台
                    System.out.println("液位高于下限且先前A3状态标志位是空包状态，空包→出铁, A3状态标志位 1，A3报警标志位 不变, A3出铁结束检测志位 1，A3castStartTime: " + A3castStartTime);
                    //先前A3状态标志位不是空包状态且出铁结束检测标志位开启，出铁→满包
                } else if (A3StatusFlag != 0 && A3PourFlag == 1) {
                    //读取历史液位数据，从Level表
                    String sqlHistoryLevel = "SELECT equipment_A3 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 30,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A3");
                    A3HistoryLevel =  runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    //调试输出控制台
                    //System.out.println("读取历史液位数据，从Level表, A3HistoryLevel: " + A3HistoryLevel);
                    //当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法
                    if ((Double.parseDouble(A3Level)  - A3HistoryLevel) < -0.01) {
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = A3Weight;
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 3;//当前铁水包位置3
                        //System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                        //A3出铁结束检测标志位 关闭
                        A3PourFlag = 0;
                        //调试输出控制台
                        System.out.println("当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，出铁结束检测标志位关闭，更新出铁结束时间，出铁合计时间，出铁吨位. 当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法, castTonnage: " + castTonnage + ", A3Level: " + A3Level + ", castEndTime: " + castEndTime);
                    }
                }
                //液位高于报警（单独判断）且先前A3报警标志位是0，执行报警方法
                if (equipmentA3 >= warningLevel && A3WarningFlag == 0) {
                    //更新出铁报警时间
                    equipmentLocation = 3;//当前铁水包位置3
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocation);
                    //A3报警标志位 置1
                    A3WarningFlag = 1;
                    //A3状态标志位 置满包
                    A3StatusFlag = 2;
                    //调试输出控制台
                    System.out.println("出铁报警, equipmentA3: " + equipmentA3 + ", warningLevel:" + warningLevel + "A3状态标志位 2， A3报警标志位 1");
                }

                //液位低于下限且先前A6状态标志位是出铁或满包状态
                if (equipmentA6 <= A6statusLowerMark && A6StatusFlag != 0) {
                    //A6状态标志位 置0
                    A6StatusFlag = 0;
                    //A6报警标志位 置0
                    A6WarningFlag = 0;
                    //调试输出控制台
                    System.out.println("液位低于下限且先前A6状态标志位是出铁或满包状态, A6状态标志位 0，A6报警标志位 0");
                    //液位高于下限且先前A6状态标志位是空包状态，空包→出铁
                } else if (equipmentA6 > A6statusLowerMark && A6StatusFlag == 0) {
                    A6castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                    equipmentLocation = 6;
                    System.out.println("当前铁水包位置:" + equipmentLocation + "开始出铁时间由SQL自动载入");
                    String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                    BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                    runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    //A6状态标志位 置出铁
                    A6StatusFlag = 1;
                    //A6出铁结束检测标志位 开启
                    A6PourFlag = 1;
                    //调试输出控制台
                    System.out.println("液位高于下限且先前A6状态标志位是空包状态，空包→出铁, A6状态标志位 1，A6报警标志位 不变, A6出铁结束检测志位 1，A6castStartTime: " + A6castStartTime);
                    //先前A6状态标志位不是空包状态且出铁结束检测标志位开启，出铁→满包
                } else if (A6StatusFlag != 0 && A6PourFlag == 1) {
                    //读取历史液位数据，从Level表
                    String sqlHistoryLevel = "SELECT equipment_A6 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 30,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A6");
                    A6HistoryLevel =  runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    //调试输出控制台
                    //System.out.println("读取历史液位数据，从Level表, A6HistoryLevel: " + A6HistoryLevel);
                    //当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法
                    if ((Double.parseDouble(A6Level)  - A6HistoryLevel) < -0.01) {
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = A6Weight;
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 6;//当前铁水包位置6
                        //System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                        //A6出铁结束检测标志位 关闭
                        A6PourFlag = 0;
                        //调试输出控制台
                        System.out.println("当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，出铁结束检测标志位关闭，更新出铁结束时间，出铁合计时间，出铁吨位. 当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法, castTonnage: " + castTonnage + ", A6Level: " + A6Level + ", castEndTime: " + castEndTime);
                    }
                }
                //液位高于报警（单独判断）且先前A6报警标志位是0，执行报警方法
                if (equipmentA6 >= warningLevel && A6WarningFlag == 0) {
                    //更新出铁报警时间
                    equipmentLocation = 6;//当前铁水包位置6
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocation);
                    //A6报警标志位 置1
                    A6WarningFlag = 1;
                    //A6状态标志位 置满包
                    A6StatusFlag = 2;
                    //调试输出控制台
                    System.out.println("出铁报警, equipmentA6: " + equipmentA6 + ", warningLevel:" + warningLevel + "A6状态标志位 2， A6报警标志位 1");
                }

                //液位低于下限且先前A7状态标志位是出铁或满包状态
                if (equipmentA7 <= A7statusLowerMark && A7StatusFlag != 0) {
                    //A7状态标志位 置0
                    A7StatusFlag = 0;
                    //A7报警标志位 置0
                    A7WarningFlag = 0;
                    //调试输出控制台
                    System.out.println("液位低于下限且先前A7状态标志位是出铁或满包状态, A7状态标志位 0，A7报警标志位 0");
                    //液位高于下限且先前A7状态标志位是空包状态，空包→出铁
                } else if (equipmentA7 > A7statusLowerMark && A7StatusFlag == 0) {
                    A7castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                    equipmentLocation = 7;
                    System.out.println("当前铁水包位置:" + equipmentLocation + "开始出铁时间由SQL自动载入");
                    String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                    BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                    runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    //A7状态标志位 置出铁
                    A7StatusFlag = 1;
                    //A7出铁结束检测标志位 开启
                    A7PourFlag = 1;
                    //调试输出控制台
                    System.out.println("液位高于下限且先前A7状态标志位是空包状态，空包→出铁, A7状态标志位 1，A7报警标志位 不变, A7出铁结束检测志位 1，A7castStartTime: " + A7castStartTime);
                    //先前A7状态标志位不是空包状态且出铁结束检测标志位开启，出铁→满包
                } else if (A7StatusFlag != 0 && A7PourFlag == 1) {
                    //读取历史液位数据，从Level表
                    String sqlHistoryLevel = "SELECT equipment_A7 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 30,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A7");
                    A7HistoryLevel =  runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    //调试输出控制台
                    //System.out.println("读取历史液位数据，从Level表, A7HistoryLevel: " + A7HistoryLevel);
                    //当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法
                    if ((Double.parseDouble(A7Level)  - A7HistoryLevel) < -0.01) {
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = A7Weight;
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 7;//当前铁水包位置7
                        //System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                        //A7出铁结束检测标志位 关闭
                        A7PourFlag = 0;
                        //调试输出控制台
                        System.out.println("当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，出铁结束检测标志位关闭，更新出铁结束时间，出铁合计时间，出铁吨位. 当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法, castTonnage: " + castTonnage + ", A7Level: " + A7Level + ", castEndTime: " + castEndTime);
                    }
                }
                //液位高于报警（单独判断）且先前A7报警标志位是0，执行报警方法
                if (equipmentA7 >= warningLevel && A7WarningFlag == 0) {
                    //更新出铁报警时间
                    equipmentLocation = 7;//当前铁水包位置7
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocation);
                    //A7报警标志位 置1
                    A7WarningFlag = 1;
                    //A7状态标志位 置满包
                    A7StatusFlag = 2;
                    //调试输出控制台
                    System.out.println("出铁报警, equipmentA7: " + equipmentA7 + ", warningLevel:" + warningLevel + "A7状态标志位 2， A7报警标志位 1");
                }

                //液位低于下限且先前A8状态标志位是出铁或满包状态
                if (equipmentA8 <= A8statusLowerMark && A8StatusFlag != 0) {
                    //A8状态标志位 置0
                    A8StatusFlag = 0;
                    //A8报警标志位 置0
                    A8WarningFlag = 0;
                    //调试输出控制台
                    System.out.println("液位低于下限且先前A8状态标志位是出铁或满包状态, A8状态标志位 0，A8报警标志位 0");
                    //液位高于下限且先前A8状态标志位是空包状态，空包→出铁
                } else if (equipmentA8 > A8statusLowerMark && A8StatusFlag == 0) {
                    A8castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                    equipmentLocation = 8;
                    System.out.println("当前铁水包位置:" + equipmentLocation + "开始出铁时间由SQL自动载入");
                    String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                    BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                    runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    //A8状态标志位 置出铁
                    A8StatusFlag = 1;
                    //A8出铁结束检测标志位 开启
                    A8PourFlag = 1;
                    //调试输出控制台
                    System.out.println("液位高于下限且先前A8状态标志位是空包状态，空包→出铁, A8状态标志位 1，A8报警标志位 不变, A8出铁结束检测志位 1，A8castStartTime: " + A8castStartTime);
                    //先前A8状态标志位不是空包状态且出铁结束检测标志位开启，出铁→满包
                } else if (A8StatusFlag != 0 && A8PourFlag == 1) {
                    //读取历史液位数据，从Level表
                    String sqlHistoryLevel = "SELECT equipment_A8 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 30,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A8");
                    A8HistoryLevel =  runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    //调试输出控制台
                    //System.out.println("读取历史液位数据，从Level表, A8HistoryLevel: " + A8HistoryLevel);
                    //当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法
                    if ((Double.parseDouble(A8Level)  - A8HistoryLevel) < -0.01) {
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = A8Weight;
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 8;//当前铁水包位置8
                        //System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                        //A8出铁结束检测标志位 关闭
                        A8PourFlag = 0;
                        //调试输出控制台
                        System.out.println("当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，出铁结束检测标志位关闭，更新出铁结束时间，出铁合计时间，出铁吨位. 当前液位减去三分钟前历史液位的值低于-0.1时，判定出铁结束，执行下列方法, castTonnage: " + castTonnage + ", A8Level: " + A8Level + ", castEndTime: " + castEndTime);
                    }
                }
                //液位高于报警（单独判断）且先前A8报警标志位是0，执行报警方法
                if (equipmentA8 >= warningLevel && A8WarningFlag == 0) {
                    //更新出铁报警时间
                    equipmentLocation = 8;//当前铁水包位置8
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocation);
                    //A8报警标志位 置1
                    A8WarningFlag = 1;
                    //A8状态标志位 置满包
                    A8StatusFlag = 2;
                    //调试输出控制台
                    System.out.println("出铁报警, equipmentA8: " + equipmentA8 + ", warningLevel:" + warningLevel + "A8状态标志位 2， A8报警标志位 1");
                }

                //输出当前液位数据给液位表
                String sqlLevel = "INSERT INTO product_blast_furnace_level(equipment_a1,equipment_a2,equipment_a3,equipment_a6,equipment_a7,equipment_a8)VALUES(?,?,?,?,?,?)";
                BeanHandler<Object> handerLevel =  new BeanHandler<Object>(CollectionMain.class);
                runner.insert(conn,sqlLevel,handerLevel,A1Level,A2Level,A3Level,A6Level,A7Level,A8Level);

                //输出当前状态数据给状态表
                String sqlStatus = "INSERT INTO product_blast_furnace_status(equipment_a1_state,equipment_a2_state,equipment_a3_state,equipment_a6_state,equipment_a7_state,equipment_a8_state)VALUES(?,?,?,?,?,?)";
                BeanHandler<Object> handerStatus =  new BeanHandler<Object>(CollectionMain.class);
                runner.insert(conn,sqlStatus,handerStatus,A1StatusFlag,A2StatusFlag,A3StatusFlag,A6StatusFlag,A7StatusFlag,A8StatusFlag);

                //计算当前重量数据
                if (equipmentA1 <= 0.93) {
                    A1Weight = String.format("%.3f", Math.PI * 7.30 * Math.pow(equipmentA1, 2) - Math.PI * 1.83 * Math.pow(equipmentA1, 3));//分段函数第一段
                } else if (equipmentA1 > 1.86) {
                    A1Weight = String.format("%.3f", Math.PI * 10.30 * equipmentA1 - Math.PI * 5.29);//分段函数第三段
                } else {
                    A1Weight = String.format("%.3f", Math.PI * 9.71 * equipmentA1 - Math.PI * 4.19);//分段函数第二段
                }
                if (equipmentA2 <= 0.93) {
                    A2Weight = String.format("%.3f", Math.PI * 7.30 * Math.pow(equipmentA2, 2) - Math.PI * 1.83 * Math.pow(equipmentA2, 3));//分段函数第一段
                } else if (equipmentA1 > 1.86) {
                    A2Weight = String.format("%.3f", Math.PI * 10.30 * equipmentA2 - Math.PI * 5.29);//分段函数第三段
                } else {
                    A2Weight = String.format("%.3f", Math.PI * 9.71 * equipmentA2 - Math.PI * 4.19);//分段函数第二段
                }
                if (equipmentA3 <= 0.93) {
                    A3Weight = String.format("%.3f", Math.PI * 7.30 * Math.pow(equipmentA3, 2) - Math.PI * 1.83 * Math.pow(equipmentA3, 3));//分段函数第一段
                } else if (equipmentA1 > 1.86) {
                    A3Weight = String.format("%.3f", Math.PI * 10.30 * equipmentA3 - Math.PI * 5.29);//分段函数第三段
                } else {
                    A3Weight = String.format("%.3f", Math.PI * 9.71 * equipmentA3 - Math.PI * 4.19);//分段函数第二段
                }
                if (equipmentA6 <= 0.93) {
                    A6Weight = String.format("%.3f", Math.PI * 7.30 * Math.pow(equipmentA6, 2) - Math.PI * 1.83 * Math.pow(equipmentA6, 3));//分段函数第一段
                } else if (equipmentA1 > 1.86) {
                    A6Weight = String.format("%.3f", Math.PI * 10.30 * equipmentA6 - Math.PI * 5.29);//分段函数第三段
                } else {
                    A6Weight = String.format("%.3f", Math.PI * 9.71 * equipmentA6 - Math.PI * 4.19);//分段函数第二段
                }
                if (equipmentA7 <= 0.93) {
                    A7Weight = String.format("%.3f", Math.PI * 7.30 * Math.pow(equipmentA7, 2) - Math.PI * 1.83 * Math.pow(equipmentA7, 3));//分段函数第一段
                } else if (equipmentA1 > 1.86) {
                    A7Weight = String.format("%.3f", Math.PI * 10.30 * equipmentA7 - Math.PI * 5.29);//分段函数第三段
                } else {
                    A7Weight = String.format("%.3f", Math.PI * 9.71 * equipmentA7 - Math.PI * 4.19);//分段函数第二段
                }
                if (equipmentA8 <= 0.93) {
                    A8Weight = String.format("%.3f", Math.PI * 7.30 * Math.pow(equipmentA8, 2) - Math.PI * 1.83 * Math.pow(equipmentA8, 3));//分段函数第一段
                } else if (equipmentA1 > 1.86) {
                    A8Weight = String.format("%.3f", Math.PI * 10.30 * equipmentA8 - Math.PI * 5.29);//分段函数第三段
                } else {
                    A8Weight = String.format("%.3f", Math.PI * 9.71 * equipmentA8 - Math.PI * 4.19);//分段函数第二段
                }
                //System.out.println("A1Weight: " + A1Weight + ", A2Weight: " + A2Weight + ", A3Weight: " + A3Weight + ", A6Weight: " + A6Weight + ", A7Weight: " + A7Weight + ", A8Weight: " + A8Weight);
                //计算当前流量
                castTotalTime = Double.valueOf(System.currentTimeMillis() - A1castStartTime);
                A1Flow = String.format("%.3f", 60000 * Double.valueOf(A1Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A2Flow = String.format("%.3f", 60000 * Double.valueOf(A2Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A3Flow = String.format("%.3f", 60000 * Double.valueOf(A3Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A6Flow = String.format("%.3f", 60000 * Double.valueOf(A6Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A7Flow = String.format("%.3f", 60000 * Double.valueOf(A7Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A8Flow = String.format("%.3f", 60000 * Double.valueOf(A8Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                //System.out.println("A1Flow: " + A1Flow + ", A2Flow: " + A2Flow + ", A3Flow: " + A3Flow + ", A6Flow: " + A6Flow + ", A7Flow: " + A7Flow + ", A8Flow: " + A8Flow);
                //输出当前重量数据给重量表
                String sqlWeight = "UPDATE product_blast_furnace_weight SET blast_furnace_level_id = (SELECT * FROM(SELECT blast_furnace_level_id FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 1) as a), equipment_a1_weight = ?, equipment_a2_weight = ?, equipment_a3_weight = ?, equipment_a6_weight = ?, equipment_a7_weight = ?, equipment_a8_weight = ?";
                runner.update(conn,sqlWeight,A1Weight,A2Weight,A3Weight,A6Weight,A7Weight,A8Weight);
                //输出当前流量数据给流量流速表
                String sqlSpeedAndFlow = "UPDATE product_blast_furnace_speed SET blast_furnace_level_id_index = (SELECT * FROM(SELECT blast_furnace_level_id FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 1) as a), " +
                        "equipment_a1_flow = ?, equipment_a2_flow = ?, equipment_a3_flow = ?, equipment_a6_flow = ?, equipment_a7_flow = ?, equipment_a8_flow = ?";
                runner.update(conn,sqlSpeedAndFlow,A1Flow,A2Flow,A3Flow,A6Flow,A7Flow,A8Flow);

                //时间测试结束
                //System.out.println(System.currentTimeMillis() - testTime);

                Thread.sleep(739);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                masterCom3.destroy();
                masterCom4.destroy();
                //关闭数据库连接
                //conn.close();
            }
        }
    }

    /**
     * 读保持寄存器上的内容
     *
     * @param master  主站
     * @param slaveId 从站地址
     * @param start   起始地址的偏移量
     * @param len     待读寄存器的个数
     */
    private static short[] readHoldingRegistersTest(ModbusMaster master, int slaveId, int start, int len) {
        try {
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(slaveId, start, len);
            ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse)master.send(request);
            if (response.isException()) {
                //System.out.println("Exception response: message=" + response.getExceptionMessage());
            } else {
                //System.out.println(Arrays.toString(response.getShortData()));
                short[] list = response.getShortData();
                return list;
            }
        } catch (ModbusTransportException e) {
            e.printStackTrace();
        }
        return null;
    }
}

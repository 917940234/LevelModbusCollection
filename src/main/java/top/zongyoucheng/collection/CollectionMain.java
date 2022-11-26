package top.zongyoucheng.collection;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.druid.sql.visitor.functions.Now;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import com.serotonin.modbus4j.serial.SerialPortWrapper;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
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
        //出铁状态变化标志，不能被循环重置
        Integer A1castStatusFlag = 0;//铁水包1
        Integer A2castStatusFlag = 0;//铁水包2
        Integer A3castStatusFlag = 0;//铁水包3
        Integer A6castStatusFlag = 0;//铁水包6
        Integer A7castStatusFlag = 0;//铁水包7
        Integer A8castStatusFlag = 0;//铁水包8
        Long A1castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A2castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A3castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A6castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A7castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        Long A8castStartTime = 0L;//开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长

        /* 读取液位并显示至数据库 */
        while (true) {
            try {
                masterCom3.init();
                masterCom4.init();
                short[] listCom3 = readHoldingRegistersTest(masterCom3, SLAVE_ADDRESS_COM3, 8, 4);
                short[] listCom4 = readHoldingRegistersTest(masterCom4, SLAVE_ADDRESS_COM4, 8, 4);

                //输出寄存器值
                //System.out.println("COM3-40009-equipmentA1: " + listCom3[0]);
                //System.out.println("COM3-40010-equipmentA2: " + listCom3[1]);
                //System.out.println("COM3-40011-equipmentA3: " + listCom3[2]);
                //System.out.println("COM3-40012-equipmentA4: " + listCom3[3] + "\n");
                //
                //System.out.println("COM4-40009-equipmentA6: " + listCom4[0]);
                //System.out.println("COM4-40010-equipmentA5: " + listCom4[1]);
                //System.out.println("COM4-40011-equipmentA7: " + listCom4[2]);
                //System.out.println("COM4-40012-equipmentA8: " + listCom4[3]);

                equipmentA1 = (Integer.valueOf(listCom3[0]) - 4000) * 0.000578125;//西出铁口40009寄存器
                equipmentA2 = (Integer.valueOf(listCom3[1]) - 4000) * 0.000578125;//西出铁口40010寄存器
                equipmentA3 = (Integer.valueOf(listCom3[2]) - 4000) * 0.000578125;//西出铁口40011寄存器
                //equipmentA4 = (Integer.valueOf(listCom3[3]) - 4000) * 0.000578125;//西出铁口40012寄存器（无效）

                equipmentA6 = (Integer.valueOf(listCom4[0]) - 4000) * 0.000578125;//东出铁口40009寄存器
                //equipmentA5 = (Integer.valueOf(listCom4[1]) - 4000) * 0.000578125;//东出铁口40010寄存器（无效）
                equipmentA7 = (Integer.valueOf(listCom4[2]) - 4000) * 0.000578125;//东出铁口40011寄存器
                equipmentA8 = (Integer.valueOf(listCom4[3]) - 4000) * 0.000578125;//东出铁口40012寄存器

                //初始化液位变量
                String A1Level = decimalFormat.format(equipmentA1);
                String A2Level = decimalFormat.format(equipmentA2);
                String A3Level = decimalFormat.format(equipmentA3);
                String A6Level = decimalFormat.format(equipmentA6);
                String A7Level = decimalFormat.format(equipmentA7);
                String A8Level = decimalFormat.format(equipmentA8);

                //初始化重量变量
                String A1Weight = null;
                String A2Weight = null;
                String A3Weight = null;
                String A6Weight = null;
                String A7Weight = null;
                String A8Weight = null;

                //初始化状态变量
                Double A1statusLowerMark = 0.1;//已根据历史数据初次设定
                Double A1statusUpperMark = 2.5;//已根据历史数据初次设定
                Double A2statusLowerMark = 0.1;//已根据历史数据初次设定
                Double A2statusUpperMark = 2.4;//已根据历史数据初次设定
                Double A3statusLowerMark = 0.1;//已根据历史数据初次设定
                Double A3statusUpperMark = 1.7;//已根据历史数据初次设定
                Double A6statusLowerMark = 0.1;//已根据历史数据初次设定
                Double A6statusUpperMark = 2.4;//已根据历史数据初次设定
                Double A7statusLowerMark = 0.3;//已根据历史数据初次设定
                Double A7statusUpperMark = 2.4;//已根据历史数据初次设定
                Double A8statusLowerMark = 0.26;//已根据历史数据初次设定
                Double A8statusUpperMark = 2.1;//已根据历史数据初次设定

                //初始化流速流量变量
                String A1Speed = null;
                String A2Speed = null;
                String A3Speed = null;
                String A6Speed = null;
                String A7Speed = null;
                String A8Speed = null;
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

                //创建状态标志位
                Integer A1Status,A2Status,A3Status,A6Status,A7Status,A8Status;
                //判断状态，给状态标志位赋值，铁水包1
                if (equipmentA1 <= A1statusLowerMark) {
                    A1Status = 0;//空包
                    if (A1castStatusFlag == 2) {//满包→空包
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = String.format("%.3f", Math.PI * 10.30 * 2.4 - Math.PI * 5.29);//最终出铁吨位，因为总液位高度差为2.4，所以采用分段函数第三段
                        System.out.println("castTonnage: " + castTonnage + ", equipmentA1: " + equipmentA1);
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 1;//当前铁水包位置1
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                    }
                    A1castStatusFlag = 0;
                } else if (equipmentA1 >= A1statusUpperMark) {
                    A1Status = 2;//满包
                    if (A1castStatusFlag == 1) {//出铁→满包
                        //更新出铁报警时间
                        equipmentLocation = 1;//当前铁水包位置1
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁报警时间由SQL语句执行");
                        String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast12,equipmentLocation);
                    }
                    A1castStatusFlag = 2;
                } else {
                    A1Status = 1;//出铁
                    if (A1castStatusFlag == 0) {//空包→出铁
                        //插入当前铁水包位置，出铁开始时间
                        //此处计算出铁开始时间是为了给重量液位模块使用
                        A1castStartTime = System.currentTimeMillis();
                        equipmentLocation = 1;//当前铁水包位置1
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",开始出铁时间由SQL自动载入");
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    }
                    A1castStatusFlag = 1;
                }
                //判断状态，给状态标志位赋值，铁水包2
                if (equipmentA2 <= A2statusLowerMark) {
                    A2Status = 0;//空包
                    if (A2castStatusFlag == 2) {//满包→空包
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = String.format("%.3f", Math.PI * 10.30 * 2.3 - Math.PI * 5.29);//最终出铁吨位，因为总液位高度差为2.3，所以采用分段函数第三段
                        System.out.println("castTonnage: " + castTonnage + ", equipmentA2: " + equipmentA2);
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 2;//当前铁水包位置2
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                    }
                    A2castStatusFlag = 0;
                } else if (equipmentA2 >= A2statusUpperMark) {
                    A2Status = 2;//满包
                    if (A2castStatusFlag == 1) {//出铁→满包
                        //更新出铁报警时间
                        equipmentLocation = 2;//当前铁水包位置2
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁报警时间由SQL语句执行");
                        String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast12,equipmentLocation);
                    }
                    A2castStatusFlag = 2;
                } else {
                    A2Status = 1;//出铁
                    if (A2castStatusFlag == 0) {//空包→出铁
                        //插入当前铁水包位置，出铁开始时间
                        //此处计算出铁开始时间是为了给重量液位模块使用
                        A2castStartTime = System.currentTimeMillis();
                        equipmentLocation = 2;//当前铁水包位置2
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",开始出铁时间由SQL自动载入");
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    }
                    A2castStatusFlag = 1;
                }
                //判断状态，给状态标志位赋值，铁水包3
                if (equipmentA3 <= A3statusLowerMark) {
                    A3Status = 0;//空包
                    if (A3castStatusFlag == 2) {//满包→空包
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = String.format("%.3f", Math.PI * 9.71 * 1.6 - Math.PI * 4.19);//最终出铁吨位，因为总液位高度差为1.6，所以采用分段函数第二段
                        System.out.println("castTonnage: " + castTonnage + ", equipmentA3: " + equipmentA3);
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 3;//当前铁水包位置3
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                    }
                    A3castStatusFlag = 0;
                } else if (equipmentA3 >= A3statusUpperMark) {
                    A3Status = 2;//满包
                    if (A3castStatusFlag == 1) {//出铁→满包
                        //更新出铁报警时间
                        equipmentLocation = 3;//当前铁水包位置3
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁报警时间由SQL语句执行");
                        String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast12,equipmentLocation);
                    }
                    A3castStatusFlag = 2;
                } else {
                    A3Status = 1;//出铁
                    if (A3castStatusFlag == 0) {//空包→出铁
                        //插入当前铁水包位置，出铁开始时间
                        //此处计算出铁开始时间是为了给重量液位模块使用
                        A3castStartTime = System.currentTimeMillis();
                        equipmentLocation = 3;//当前铁水包位置3
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",开始出铁时间由SQL自动载入");
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    }
                    A3castStatusFlag = 1;
                }
                //判断状态，给状态标志位赋值，铁水包6
                if (equipmentA6 <= A6statusLowerMark) {
                    A6Status = 0;//空包
                    if (A6castStatusFlag == 2) {//满包→空包
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = String.format("%.3f", Math.PI * 10.304181 * 2.3 - Math.PI * 5.29103691);//最终出铁吨位，因为总液位高度差为2.3，所以采用分段函数第三段
                        System.out.println("castTonnage: " + castTonnage + ", equipmentA6: " + equipmentA6);
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 6;//当前铁水包位置6
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                    }
                    A6castStatusFlag = 0;
                } else if (equipmentA6 >= A6statusUpperMark) {
                    A6Status = 2;//满包
                    if (A6castStatusFlag == 1) {//出铁→满包
                        //更新出铁报警时间
                        equipmentLocation = 6;//当前铁水包位置6
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁报警时间由SQL语句执行");
                        String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast12,equipmentLocation);
                    }
                    A6castStatusFlag = 2;
                } else {
                    A6Status = 1;//出铁
                    if (A6castStatusFlag == 0) {//空包→出铁
                        //插入当前铁水包位置，出铁开始时间
                        //此处计算出铁开始时间是为了给重量液位模块使用
                        A6castStartTime = System.currentTimeMillis();
                        equipmentLocation = 6;//当前铁水包位置6
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",开始出铁时间由SQL自动载入");
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    }
                    A6castStatusFlag = 1;
                }
                //判断状态，给状态标志位赋值，铁水包7
                if (equipmentA7 <= A7statusLowerMark) {
                    A7Status = 0;//空包
                    if (A7castStatusFlag == 2) {//满包→空包
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = String.format("%.3f", Math.PI * 10.304181 * 2.1 - Math.PI * 5.29103691);//最终出铁吨位，因为总液位高度差为2.1，所以采用分段函数第三段
                        System.out.println("castTonnage: " + castTonnage + ", equipmentA7: " + equipmentA7);
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 7;//当前铁水包位置7
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                    }
                    A7castStatusFlag = 0;
                } else if (equipmentA7 >= A7statusUpperMark) {
                    A7Status = 2;//满包
                    if (A7castStatusFlag == 1) {//出铁→满包
                        //更新出铁报警时间
                        equipmentLocation = 7;//当前铁水包位置7
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁报警时间由SQL语句执行");
                        String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast12,equipmentLocation);
                    }
                    A7castStatusFlag = 2;
                } else {
                    A7Status = 1;//出铁
                    if (A7castStatusFlag == 0) {//空包→出铁
                        //插入当前铁水包位置，出铁开始时间
                        //此处计算出铁开始时间是为了给重量液位模块使用
                        A7castStartTime = System.currentTimeMillis();
                        equipmentLocation = 7;//当前铁水包位置7
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",开始出铁时间由SQL自动载入");
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    }
                    A7castStatusFlag = 1;
                }
                //判断状态，给状态标志位赋值，铁水包8
                if (equipmentA8 <= A8statusLowerMark) {
                    A8Status = 0;//空包
                    if (A8castStatusFlag == 2) {//满包→空包
                        //更新出铁结束时间，出铁合计时间，出铁吨位
                        castTonnage = String.format("%.3f", Math.PI * 9.71 * 1.84 - Math.PI * 4.19);//最终出铁吨位，因为总液位高度差为1.84，所以采用分段函数第二段
                        System.out.println("castTonnage: " + castTonnage + ", equipmentA8: " + equipmentA8);
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        equipmentLocation = 8;//当前铁水包位置8
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁结束时间" + castEndTime + ",出铁合计时间通过SQL计算");
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast20, equipmentLocation, castTonnage, equipmentLocation);
                    }
                    A8castStatusFlag = 0;
                } else if (equipmentA8 >= A8statusUpperMark) {
                    A8Status = 2;//满包
                    if (A8castStatusFlag == 1) {//出铁→满包
                        //更新出铁报警时间
                        equipmentLocation = 8;//当前铁水包位置8
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",出铁报警时间由SQL语句执行");
                        String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn,sqlCast12,equipmentLocation);
                    }
                    A8castStatusFlag = 2;
                } else {
                    A8Status = 1;//出铁
                    if (A8castStatusFlag == 0) {//空包→出铁
                        //插入当前铁水包位置，出铁开始时间
                        //此处计算出铁开始时间是为了给重量液位模块使用
                        A8castStartTime = System.currentTimeMillis();
                        equipmentLocation = 8;//当前铁水包位置8
                        System.out.println("当前铁水包位置:" + equipmentLocation + ",开始出铁时间由SQL自动载入");
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocation);
                    }
                    A8castStatusFlag = 1;
                }

                //输出数据库值
                //System.out.println("液位值:" + A1Level + "," + A2Level + "," + A3Level + "," + A6Level + "," + A7Level + "," + A8Level);
                //System.out.println("状态值:" + A1Status + "," + A2Status + "," + A3Status + "," + A6Status + "," + A7Status + "," + A8Status);

                //输出当前液位数据给液位表
                String sqlLevel = "INSERT INTO product_blast_furnace_level(equipment_a1,equipment_a2,equipment_a3,equipment_a6,equipment_a7,equipment_a8)VALUES(?,?,?,?,?,?)";
                BeanHandler<Object> handerLevel =  new BeanHandler<Object>(CollectionMain.class);
                runner.insert(conn,sqlLevel,handerLevel,A1Level,A2Level,A3Level,A6Level,A7Level,A8Level);

                //输出当前状态数据给状态表
                String sqlStatus = "INSERT INTO product_blast_furnace_status(equipment_a1_state,equipment_a2_state,equipment_a3_state,equipment_a6_state,equipment_a7_state,equipment_a8_state)VALUES(?,?,?,?,?,?)";
                BeanHandler<Object> handerStatus =  new BeanHandler<Object>(CollectionMain.class);
                runner.insert(conn,sqlStatus,handerStatus,A1Status,A2Status,A3Status,A6Status,A7Status,A8Status);

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
                System.out.println("A1Weight: " + A1Weight + ", A2Weight: " + A2Weight + ", A3Weight: " + A3Weight + ", A6Weight: " + A6Weight + ", A7Weight: " + A7Weight + ", A8Weight: " + A8Weight);
                //计算当前流量流速(t/min)
                castTotalTime = Double.valueOf(System.currentTimeMillis() - A1castStartTime);
                A1Speed = String.format("%.3f", 60000 * Double.valueOf(A1Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A1Flow = A1Speed;
                A2Speed = String.format("%.3f", 60000 * Double.valueOf(A2Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A2Flow = A2Speed;
                A3Speed = String.format("%.3f", 60000 * Double.valueOf(A3Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A3Flow = A3Speed;
                A6Speed = String.format("%.3f", 60000 * Double.valueOf(A6Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A6Flow = A6Speed;
                A7Speed = String.format("%.3f", 60000 * Double.valueOf(A7Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A7Flow = A7Speed;
                A8Speed = String.format("%.3f", 60000 * Double.valueOf(A8Weight)/castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A8Flow = A8Speed;
                System.out.println("A1Speed: " + A1Speed + ", A2Speed: " + A2Speed + ", A3Speed: " + A3Speed + ", A6Speed: " + A6Speed + ", A7Speed: " + A7Speed + ", A8Speed: " + A8Speed);
                //输出当前重量数据给重量表
                String sqlWeight = "UPDATE product_blast_furnace_weight SET blast_furnace_level_id = (SELECT * FROM(SELECT blast_furnace_level_id FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 1) as a), equipment_a1_weight = ?, equipment_a2_weight = ?, equipment_a3_weight = ?, equipment_a6_weight = ?, equipment_a7_weight = ?, equipment_a8_weight = ?";
                runner.update(conn,sqlWeight,A1Weight,A2Weight,A3Weight,A6Weight,A7Weight,A8Weight);
                //输出当前流量流速数据给流量流速表
                String sqlSpeedAndFlow = "UPDATE product_blast_furnace_speed SET blast_furnace_level_id_index = (SELECT * FROM(SELECT blast_furnace_level_id FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 1) as a), " +
                        "equipment_a1_speed = ?, equipment_a2_speed = ?, equipment_a3_speed = ?, equipment_a6_speed = ?, equipment_a7_speed = ?, equipment_a8_speed = ?, " +
                        "equipment_a1_flow = ?, equipment_a2_flow = ?, equipment_a3_flow = ?, equipment_a6_flow = ?, equipment_a7_flow = ?, equipment_a8_flow = ?";
                runner.update(conn,sqlSpeedAndFlow,A1Speed,A2Speed,A3Speed,A6Speed,A7Speed,A8Speed,A1Flow,A2Flow,A3Flow,A6Flow,A7Flow,A8Flow);

                Thread.sleep(1000);
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

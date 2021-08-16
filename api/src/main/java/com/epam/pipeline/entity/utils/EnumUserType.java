package com.epam.pipeline.entity.utils;

import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

@RequiredArgsConstructor
public class EnumUserType<T extends Enum<T>> implements UserType {

    private final Class<T> enumClass;

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    public Class<?> returnedClass() {
        return enumClass;
    }

    @Override
    public boolean equals(final Object x, final Object y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(final Object x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public Object nullSafeGet(final ResultSet rs, final String[] names, final SessionImplementor session,
                              final Object owner) throws SQLException {
        final Object o = rs.getObject(names[0]);
        return o == null ? null : Enum.valueOf(enumClass, o.toString());
    }

    @Override
    public void nullSafeSet(final PreparedStatement st, final Object value, final int index,
                            final SessionImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, enumClass.cast(value).name().toUpperCase(), Types.OTHER);
        }
    }

    @Override
    public T deepCopy(final Object value) {
        return enumClass.isInstance(value) ? enumClass.cast(value) : null;
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(final Object value) {
        return deepCopy(value);
    }

    @Override
    public Object assemble(final Serializable cached, final Object owner) {
        return deepCopy(cached);
    }

    @Override
    public Object replace(final Object original, final Object target, final Object owner) {
        return deepCopy(original);
    }
}

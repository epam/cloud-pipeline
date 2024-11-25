import classNames from 'classnames';

export const Logo = (props: { onClick?: () => void }) => {
  const { onClick } = props;
  return (
    <h1
      className={classNames('text-white', 'text-xl', {
        'cursor-pointer': Boolean(onClick),
      })}
      onClick={onClick}>
      Magellan NGS
    </h1>
  );
};
